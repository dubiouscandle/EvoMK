package com.dubiouscandle.evo

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils.floor
import com.badlogic.gdx.physics.box2d.Box2DDebugRenderer
import com.badlogic.gdx.utils.ScreenUtils
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class Main : ApplicationAdapter() {
    companion object {
//        val seed = 6272464531931
         val seed = System.nanoTime()
        val random = Random(seed)

        init {
            println("SEED: $seed")
        }
    }

    private val populationSize = 2000
    private val elitism = floor(populationSize * 0.1f)
    private val playbackCount = 1
    private val simulationTicks = 1200
    private var generation = 1

    private lateinit var camera: OrthographicCamera
    private lateinit var hudCamera: OrthographicCamera
    private lateinit var batch: SpriteBatch
    private lateinit var font: BitmapFont
    private lateinit var debugRenderer: Box2DDebugRenderer
    private lateinit var shapeRenderer: ShapeRenderer

    private var population = ArrayList<Genome>()
    private val config = MutatorConfig()
    private val mutator: SimpleMutator = SimpleMutator(Random(random.nextLong()), config)

    private val threadCount = Runtime.getRuntime().availableProcessors()
    private val executor = Executors.newFixedThreadPool(threadCount)

    private var playbackSimulators = ArrayList<SoloSimulator>()
    private var playbackTick = 0
    private var isSimulating = false
    private var watchPlayback = false

    private var isRecording = false
    private var ffmpegProcess: Process? = null
    private var ffmpegOut: OutputStream? = null
    private var lastMilestoneFitness = 0f
    private var previousWatchPlayback = false

    private val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
    private val runOutputDir = File("assets/run_${timestamp}_seed_$seed")
    private val fitnessLogFile by lazy { File(runOutputDir, "fitness_history.csv") }

    private val tmpBodies =
        com.badlogic.gdx.utils.Array<com.badlogic.gdx.physics.box2d.Body>()
    private val v1 =
        com.badlogic.gdx.math.Vector2()
    private val v2 =
        com.badlogic.gdx.math.Vector2()
    private val v3 =
        com.badlogic.gdx.math.Vector2()
    private val v4 =
        com.badlogic.gdx.math.Vector2()

    override fun create() {
        if (!runOutputDir.exists()) {
            runOutputDir.mkdirs()
        }
        println("Run artifacts saving to: ${runOutputDir.path}")

        fitnessLogFile.writeText("generation,best_fitness,brain_nodes,brain_connections\n")

        camera = OrthographicCamera()
        camera.setToOrtho(false, 40f, 30f)
        camera.position.set(0f, 5f, 0f)

        hudCamera = OrthographicCamera()
        hudCamera.setToOrtho(false, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())

        batch = SpriteBatch()
        font = BitmapFont()
        font.setUseIntegerPositions(false)
        debugRenderer = Box2DDebugRenderer()
        shapeRenderer = ShapeRenderer()

        Gdx.input.inputProcessor = object :
            com.badlogic.gdx.InputAdapter() {
            override fun scrolled(
                amountX: Float,
                amountY: Float
            ): Boolean {
                camera.zoom += amountY * 0.1f
                if (camera.zoom < 0.1f) camera.zoom =
                    0.1f
                camera.update()
                return true
            }
        }

        for (i in 0 until populationSize) {
            population.add(createStarterGenome())
        }

        startEvolutionCycle()
    }

    private fun createStarterGenome(): Genome {
        val anatomy = Anatomy()

        val nodeCount = 4
        for (i in 0 until nodeCount) {
            val angle =
                i * (Math.PI * 2) / nodeCount
            val node = Anatomy.Node(
                (Math.cos(angle) * 2.0).toFloat(),
                (Math.sin(angle) * 2.0).toFloat(),
                1f,
                0.5f,
                0.1f
            )
            node.sensors.add(Anatomy.Sensor(range = 0.55f))
            anatomy.nodes.add(node)
        }
        for (i in 0 until nodeCount) {
            for (j in i + 1 until nodeCount) {
                anatomy.edges.add(
                    Anatomy.Edge(
                        anatomy.nodes[i],
                        anatomy.nodes[j],
                        4f,
                        0.5f,
                        0.5f
                    )
                )
            }
        }

        val brain = Brain()

        for (node in anatomy.nodes) {
            for (addr in longArrayOf(
                node.xOffsetAddress,
                node.yOffsetAddress,
                node.xVelocityAddress,
                node.yVelocityAddress,
                node.contactAddress
            )) {
                val n = Brain.Node()
                brain.nodes.add(n)
                brain.bindings.put(addr, n)
            }
            for (sensor in node.sensors) {
                val n = Brain.Node()
                brain.nodes.add(n)
                brain.bindings.put(sensor.inputAddress, n)
            }
        }
        for (e in anatomy.edges) {
            for (addr in longArrayOf(e.contractionInputAddress, e.contractionOutputAddress)) {
                val n = Brain.Node()
                brain.nodes.add(n)
                brain.bindings.put(addr, n)
            }
        }

        val biasNode = Brain.Node()
        brain.nodes.add(biasNode)
        brain.bindings.put(Creature.BIAS_ADDRESS, biasNode)

        return Genome(anatomy, brain)
    }

    private fun startEvolutionCycle() {
        isSimulating = true

        Thread {
            val fitnessMap = ConcurrentHashMap<Genome, Float>()

            val tasks = population.map { genome ->
                Callable {
                    val sim = RunningSimulator(genome)
                    for (i in 0 until simulationTicks) {
                        sim.update(1f / 60f)
                    }
                    val fitness = sim.getFitness()
                    sim.dispose()
                    fitnessMap[genome] = fitness
                }
            }
            executor.invokeAll(tasks)

            val sortedGenomes = population.sortedByDescending { fitnessMap[it] ?: 0f }

            Gdx.app.postRunnable {
                preparePlayback(
                    sortedGenomes,
                    fitnessMap[sortedGenomes[0]] ?: 0f
                )
            }
        }.start()
    }

    private fun preparePlayback(sortedGenomes: List<Genome>, bestFitness: Float) {
        val bestGenome = sortedGenomes[0]
        val brainNodes = bestGenome.brain.nodes.size
        val brainConns = bestGenome.brain.connections.size

        println("Generation $generation - Best Fitness: $bestFitness,  Brain Size: $brainNodes nodes, $brainConns connections")

        fitnessLogFile.appendText("$generation,$bestFitness,$brainNodes,$brainConns\n")

        playbackSimulators.clear()
        for (i in 0 until playbackCount) {
            playbackSimulators.add(RunningSimulator(sortedGenomes[i]))
        }
        playbackTick = 0
        isSimulating = false

        population.clear()

        val elites = sortedGenomes.take(elitism)
        population.addAll(elites.map { it.deepClone() })

        while (population.size < populationSize) {
            val parentIndex = minOf(
                random.nextInt(populationSize),
                minOf(random.nextInt(populationSize), random.nextInt(populationSize))
            )
            val parent = sortedGenomes[parentIndex]
            val child = parent.deepClone()
            mutator.mutate(child)
            population.add(child)
        }

        if (bestFitness >= lastMilestoneFitness + 20f) {
            previousWatchPlayback = watchPlayback
            watchPlayback = true
            startRecording(generation, bestFitness)
            lastMilestoneFitness = bestFitness
        }

        generation++
    }

    private fun startRecording(gen: Int, fitness: Float) {
        try {
            val width = Gdx.graphics.backBufferWidth
            val height = Gdx.graphics.backBufferHeight
            val fitInt = fitness.toInt()
            val videoFile = File(runOutputDir, "milestone_gen_${gen}_fit_${fitInt}.mp4")

            val pb = ProcessBuilder(
                "ffmpeg", "-y",
                "-f", "rawvideo",
                "-vcodec", "rawvideo",
                "-s", "${width}x${height}",
                "-pix_fmt", "rgba",
                "-r", "60",
                "-i", "-",
                "-c:v", "libx264",
                "-preset", "ultrafast",
                "-pix_fmt", "yuv420p",
                "-vf", "vflip",
                videoFile.absolutePath
            )
            pb.redirectErrorStream(true)
            ffmpegProcess = pb.start()
            ffmpegOut = ffmpegProcess?.outputStream
            isRecording = true
            println("Started recording: ${videoFile.path}")

            Thread {
                val scanner = Scanner(ffmpegProcess!!.inputStream)
                while (scanner.hasNextLine()) scanner.nextLine()
            }.start()

        } catch (e: Exception) {
            e.printStackTrace()
            isRecording = false
            watchPlayback = previousWatchPlayback
        }
    }

    private fun recordFrame() {
        if (!isRecording || ffmpegOut == null) return
        try {
            val pixels = ScreenUtils.getFrameBufferPixels(0, 0, Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight, false)
            ffmpegOut?.write(pixels)
        } catch (e: Exception) {
            e.printStackTrace()
            stopRecording()
        }
    }

    private fun stopRecording() {
        if (!isRecording) return
        println("Stopping recording...")
        try {
            ffmpegOut?.flush()
            ffmpegOut?.close()
            ffmpegProcess?.waitFor()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        isRecording = false
        ffmpegProcess = null
        ffmpegOut = null
        watchPlayback = previousWatchPlayback
    }

    override fun render() {
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.1f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            if (!isRecording) {
                watchPlayback = !watchPlayback
            } else {
                println("Cannot skip playback while video is recording!")
            }
        }

        if (isSimulating) {
            batch.projectionMatrix = hudCamera.combined
            batch.begin()
            font.draw(
                batch,
                "Simulating Generation $generation across $threadCount threads...",
                20f,
                Gdx.graphics.height - 20f
            )
            font.draw(
                batch,
                "[Playback: ${if (watchPlayback) "ON" else "OFF"}] Press SPACE to toggle",
                20f,
                Gdx.graphics.height - 40f
            )
            batch.end()
            return
        }

        if (playbackTick < simulationTicks) {
            if (!watchPlayback) {
                playbackTick = simulationTicks
            } else {
                for (sim in playbackSimulators) {
                    sim.update(1f / 60f)
                }
                playbackTick++

                if (Gdx.input.isTouched) {
                    camera.translate(
                        -Gdx.input.deltaX * (camera.viewportWidth / Gdx.graphics.width) * 10f,
                        Gdx.input.deltaY * (camera.viewportHeight / Gdx.graphics.height) * 10f
                    )
                } else {
                    if (playbackSimulators.isNotEmpty() && playbackSimulators[0].creature.bodies.isNotEmpty()) {
                        val leadBody = playbackSimulators[0].creature.bodies[0]

                        camera.position.x += (leadBody.position.x - camera.position.x) * 0.1f
                        camera.position.y += (leadBody.position.y - camera.position.y) * 0.1f
                    }
                }
                camera.update()
            }
        } else {
            if (isRecording) stopRecording()
            for (sim in playbackSimulators) sim.dispose()
            playbackSimulators.clear()
            startEvolutionCycle()
            return
        }

        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        shapeRenderer.projectionMatrix = camera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)

        shapeRenderer.color = Color.DARK_GRAY
        for (i in -10..200) {
            val markerX = i * 10f
            shapeRenderer.rectLine(markerX, -50f, markerX, 50f, 0.05f)
        }

        shapeRenderer.color = Color.DARK_GRAY
        for (sim in playbackSimulators) {
            sim.world.getBodies(tmpBodies)
            for (body in tmpBodies) {
                if (!sim.creature.bodies.contains(body)) {
                    for (fixture in body.fixtureList) {
                        val shape = fixture.shape
                        if (shape is com.badlogic.gdx.physics.box2d.PolygonShape && shape.vertexCount == 4) {
                            shape.getVertex(0, v1)
                            shape.getVertex(1, v2)
                            shape.getVertex(2, v3)
                            shape.getVertex(3, v4)
                            v1.set(body.getWorldPoint(v1))
                            v2.set(body.getWorldPoint(v2))
                            v3.set(body.getWorldPoint(v3))
                            v4.set(body.getWorldPoint(v4))
                            shapeRenderer.triangle(
                                v1.x,
                                v1.y,
                                v2.x,
                                v2.y,
                                v3.x,
                                v3.y
                            )
                            shapeRenderer.triangle(
                                v1.x,
                                v1.y,
                                v3.x,
                                v3.y,
                                v4.x,
                                v4.y
                            )
                        } else if (shape is com.badlogic.gdx.physics.box2d.CircleShape) {
                            val worldCenter =
                                body.getWorldPoint(shape.position)
                            shapeRenderer.circle(
                                worldCenter.x,
                                worldCenter.y,
                                shape.radius,
                                16
                            )
                        }
                    }
                }
            }
        }

        shapeRenderer.color = Color(1f, 0f, 0f, 0.25f)
        for (sim in playbackSimulators) {
            for (i in 0 until sim.creature.genome.anatomy.nodes.size) {
                val node = sim.creature.genome.anatomy.nodes[i]
                val body = sim.creature.bodies[i]
                for (sensor in node.sensors) {
                    shapeRenderer.circle(
                        body.position.x,
                        body.position.y,
                        sensor.range,
                        36
                    )
                }
            }
        }

        shapeRenderer.color = Color.WHITE
        for (sim in playbackSimulators) {
            for (edge in sim.creature.edges) {
                val bodyA = sim.creature.nodeMap[edge.nodeA]!!
                val bodyB = sim.creature.nodeMap[edge.nodeB]!!
                shapeRenderer.rectLine(
                    bodyA.position.x,
                    bodyA.position.y,
                    bodyB.position.x,
                    bodyB.position.y,
                    0.1f
                )
            }
        }

        shapeRenderer.color = Color.WHITE
        for (sim in playbackSimulators) {
            for (i in 0 until sim.creature.bodies.size) {
                val body = sim.creature.bodies[i]
                val mass = sim.creature.genome.anatomy.nodes[i].mass
                shapeRenderer.circle(
                    body.position.x,
                    body.position.y,
                    kotlin.math.sqrt(mass) * 0.4f,
                    16
                )
            }
        }

        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        batch.projectionMatrix = camera.combined
        batch.begin()
        font.data.setScale(0.05f)
        for (i in -10..200) {
            val markerX = i * 10f
            font.draw(batch, "${i * 10}m", markerX + 0.2f, 0f)
        }
        font.data.setScale(1f)
        batch.end()

        batch.projectionMatrix = hudCamera.combined
        batch.begin()
        font.draw(
            batch,
            "Generation: ${generation - 1} | Playback Frame: $playbackTick / $simulationTicks | Best Fitness: ${lastMilestoneFitness.toInt()}",
            20f,
            Gdx.graphics.height - 20f
        )

        val recordStatus = if (isRecording) "[REC] " else ""
        font.draw(
            batch,
            "$recordStatus[Playback: ${if (watchPlayback) "ON" else "OFF"}] Press SPACE to toggle",
            20f,
            Gdx.graphics.height - 40f
        )
        batch.end()

        if (isRecording) {
            recordFrame()
        }
    }

    override fun dispose() {
        if (isRecording) stopRecording()
        batch.dispose()
        font.dispose()
        debugRenderer.dispose()
        shapeRenderer.dispose()
        executor.shutdown()
        for (sim in playbackSimulators) sim.dispose()
    }
}
