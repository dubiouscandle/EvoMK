package com.dubiouscandle.evo

import com.badlogic.gdx.math.Vector2
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

data class MutatorConfig(
    var brainOnly: Float = 0f,
    var brainSD: Float = 0.1f,
    var weightMutationChance: Float = 0.15f,
    var weightSD: Float = 0.01f,
    var addNodeChance: Float = 0.02f,
    var removeNodeChance: Float = 0.01f,
    var addEdgeChance: Float = 0.15f,
    var removeEdgeChance: Float = 0.01f,
    var nodePosChance: Float = 0.17f,
    var nodePosSD: Float = 0.05f,
    var positionLimiter: ((Float, Float) -> Vector2)? = null,
    var sensorRangeSD: Float = 0.02f,
    var minSensorRange: Float = 0.1f,
    var maxSensorRange: Float = 100f,
    var bodyGrowthChance: Float = 0.0001f,
    var bodyRemoveChance: Float = 0.0001f,
    var edgeGrowthChance: Float = 0.001f,
    var edgeRemoveChance: Float = 0.001f,
    var rotationSD: Float = TAU * 0.005f,
)

/*
Gaussian mutator for genomes
 */
class SimpleMutator(
    val random: Random,
    val config: MutatorConfig = MutatorConfig()
) {
    fun mutate(genome: Genome) {
        mutateBrain(genome.brain)
        if (!random.nextBoolean(config.brainOnly)) {
            mutateNodeProperties(genome.anatomy)
            mutateSensorProperties(genome.anatomy)
            mutateRotation(genome.anatomy)
            mutateTopology(genome)
        }
    }

    private fun mutateBrain(brain: Brain) {
        for (conn in brain.connections) {
            if (random.nextBoolean(config.weightMutationChance)) {
                conn.weight += random.gaussianf(config.weightSD)
            }
        }
        for (node in brain.nodes) {
            if (random.nextBoolean(config.weightMutationChance)) {
                node.bias += random.gaussianf(config.brainSD)
            }
        }


        if (brain.connections.isNotEmpty() && random.nextBoolean(config.addNodeChance)) {
            val connIndex = random.nextInt(brain.connections.size)
            val oldConn = brain.connections[connIndex]
            val newNode = Brain.Node()
            brain.nodes.add(newNode)
            brain.connections.add(Brain.Connection(oldConn.from, newNode, 1f))
            brain.connections.add(Brain.Connection(newNode, oldConn.to, oldConn.weight))
            brain.connections.removeAt(connIndex)
        }

        if (brain.nodes.isNotEmpty() && random.nextBoolean(config.addEdgeChance)) {
            val nodeA = brain.nodes[random.nextInt(brain.nodes.size)]
            val nodeB = brain.nodes[random.nextInt(brain.nodes.size)]
            val exists = brain.connections.any { it.from === nodeA && it.to === nodeB }
            if (!exists) {
                brain.connections.add(Brain.Connection(nodeA, nodeB, random.gaussianf(config.weightSD)))
            }
        }

        if (brain.connections.isNotEmpty() && random.nextBoolean(config.removeEdgeChance)) {
            brain.connections.removeAt(random.nextInt(brain.connections.size))
        }

        if (brain.nodes.size > brain.bindings.size && random.nextBoolean(config.removeNodeChance)) {
            val boundNodes = HashSet<Brain.Node>()
            for (node in brain.bindings.values()) {
                boundNodes.add(node)
            }
            val hiddenNodes = brain.nodes.filter { it !in boundNodes }
            if (hiddenNodes.isNotEmpty()) {
                val nodeToRemove = hiddenNodes[random.nextInt(hiddenNodes.size)]
                brain.nodes.remove(nodeToRemove)
                brain.connections.removeAll { it.from === nodeToRemove || it.to === nodeToRemove }
            }
        }
    }

    private fun mutateSensorProperties(anatomy: Anatomy) {
        for (node in anatomy.nodes) {
            for (sensor in node.sensors) {
                sensor.range += random.gaussianf(config.sensorRangeSD)
                sensor.range = sensor.range.coerceIn(config.minSensorRange, config.maxSensorRange)
            }
        }
    }

    private fun mutateNodeProperties(anatomy: Anatomy) {
        for (node in anatomy.nodes) {
            if (!random.nextBoolean(config.nodePosChance)) {
                continue
            }
            val shiftAmount = random.gaussianf(config.nodePosSD)
            val angle = random.nextFloat() * TAU
            node.x += shiftAmount * cos(angle)
            node.y += shiftAmount * sin(angle)
            if (config.positionLimiter != null) {
                val pos = config.positionLimiter!!(node.x, node.y)
                node.x = pos.x
                node.y = pos.y
            }
        }
    }

    private fun mutateRotation(anatomy: Anatomy) {
        val angle = random.gaussianf(config.rotationSD)
        val sin = sin(angle)
        val cos = cos(angle)

        var centerX = 0f
        var centerY = 0f
        var mass = 0f
        for (node in anatomy.nodes) {
            centerX += node.x * node.mass
            centerY += node.y * node.mass
            mass += node.mass
        }
        centerX /= mass
        centerY /= mass

        for (node in anatomy.nodes) {
            val relativeX = node.x - centerX
            val relativeY = node.y - centerY

            val rotatedX = relativeX * cos - relativeY * sin
            val rotatedY = relativeX * sin + relativeY * cos

            node.x = centerX + rotatedX
            node.y = centerY + rotatedY
        }
    }

    private fun mutateTopology(genome: Genome) {
        val anatomy = genome.anatomy
        val brain = genome.brain

        if (anatomy.edges.isNotEmpty() && random.nextBoolean(config.bodyGrowthChance)) {
            val boundEdges = anatomy.edges.filter {
                brain.bindings.containsKey(it.contractionInputAddress) &&
                    brain.bindings.containsKey(it.contractionOutputAddress)
            }
            if (boundEdges.isNotEmpty()) {
                val targetEdge = boundEdges[random.nextInt(boundEdges.size)]
                val nodeA = targetEdge.nodeA
                val nodeB = targetEdge.nodeB

                val newNode = Anatomy.Node(
                    x = (nodeA.x + nodeB.x) / 2f,
                    y = (nodeA.y + nodeB.y) / 2f,
                    mass = (nodeA.mass + nodeB.mass) / 2,
                    friction = (nodeA.friction + nodeB.friction) / 2,
                    restitution = (nodeA.restitution + nodeB.restitution) / 2
                )
                newNode.sensors.add(Anatomy.Sensor(range = 0.55f))

                brain.cloneInput(
                    nodeA.xOffsetAddress,
                    newNode.xOffsetAddress
                )
                brain.cloneInput(
                    nodeA.yOffsetAddress,
                    newNode.yOffsetAddress
                )
                brain.cloneInput(
                    nodeA.xVelocityAddress,
                    newNode.xVelocityAddress
                )
                brain.cloneInput(
                    nodeA.yVelocityAddress,
                    newNode.yVelocityAddress
                )
                brain.cloneInput(
                    nodeA.contactAddress,
                    newNode.contactAddress
                )

                if (nodeA.sensors.isNotEmpty()) {
                    brain.cloneInput(
                        nodeA.sensors[0].inputAddress,
                        newNode.sensors[0].inputAddress
                    )
                } else {
                    val n = Brain.Node()
                    brain.nodes.add(n)
                    brain.bindings.put(newNode.sensors[0].inputAddress, n)
                }

                anatomy.nodes.add(newNode)

                anatomy.edges.remove(targetEdge)

                val edge1 = Anatomy.Edge(
                    nodeA,
                    newNode,
                    targetEdge.stiffness,
                    targetEdge.damping,
                    targetEdge.compressionRatio
                )
                val edge2 = Anatomy.Edge(
                    newNode,
                    nodeB,
                    targetEdge.stiffness,
                    targetEdge.damping,
                    targetEdge.compressionRatio
                )
                anatomy.edges.add(edge1)
                anatomy.edges.add(edge2)

                brain.cloneInputToMany(
                    targetEdge.contractionInputAddress,
                    listOf(edge1.contractionInputAddress, edge2.contractionInputAddress)
                )
                brain.cloneOutputToMany(
                    targetEdge.contractionOutputAddress,
                    listOf(edge1.contractionOutputAddress, edge2.contractionOutputAddress)
                )

                val closestNode = anatomy.nodes.filter { it !== newNode && it !== nodeA && it !== nodeB }
                    .minByOrNull { (it.x - newNode.x) * (it.x - newNode.x) + (it.y - newNode.y) * (it.y - newNode.y) }

                if (closestNode != null) {
                    val stableEdge = Anatomy.Edge(newNode, closestNode, 5f, 0.5f, 0.5f)
                    anatomy.edges.add(stableEdge)
                    for (addr in longArrayOf(stableEdge.contractionInputAddress, stableEdge.contractionOutputAddress)) {
                        val n = Brain.Node()
                        brain.nodes.add(n)
                        brain.bindings.put(addr, n)
                    }
                }
                brain.removeNode(targetEdge.contractionOutputAddress)
                brain.removeNode(targetEdge.contractionInputAddress)
            }

        }

        if (anatomy.nodes.size > 1 && random.nextBoolean(config.bodyRemoveChance)) {
            val nodeToRemove =
                anatomy.nodes[random.nextInt(anatomy.nodes.size)]
            anatomy.nodes.remove(nodeToRemove)

            val edgesToRemove =
                anatomy.edges.filter { it.nodeA === nodeToRemove || it.nodeB === nodeToRemove }
            anatomy.edges.removeAll(edgesToRemove)

            brain.removeNode(nodeToRemove.xOffsetAddress)
            brain.removeNode(nodeToRemove.yOffsetAddress)
            brain.removeNode(nodeToRemove.xVelocityAddress)
            brain.removeNode(nodeToRemove.yVelocityAddress)
            brain.removeNode(nodeToRemove.contactAddress)
            for (sensor in nodeToRemove.sensors) {
                brain.removeNode(sensor.inputAddress)
            }
            for (edge in edgesToRemove) {
                brain.removeNode(edge.contractionInputAddress)
                brain.removeNode(edge.contractionOutputAddress)
            }
        }
        if (anatomy.nodes.size > 1 && random.nextBoolean(config.edgeGrowthChance)) {
            val nodeA = anatomy.nodes[random.nextInt(anatomy.nodes.size)]
            val nodeB = anatomy.nodes[random.nextInt(anatomy.nodes.size)]
            if (nodeA !== nodeB) {
                val edgeExists = anatomy.edges.any {
                    (it.nodeA === nodeA && it.nodeB === nodeB) ||
                        (it.nodeA === nodeB && it.nodeB === nodeA)
                }
                if (!edgeExists) {
                    val cloneSource = anatomy.edges.firstOrNull {
                        it.nodeA === nodeA || it.nodeB === nodeA ||
                            it.nodeA === nodeB || it.nodeB === nodeB
                    }

                    val newEdge = Anatomy.Edge(nodeA, nodeB, 5f, 0.5f, 0.5f)
                    anatomy.edges.add(newEdge)

                    if (cloneSource != null) {
                        brain.cloneInput(cloneSource.contractionInputAddress, newEdge.contractionInputAddress)
                        brain.cloneOutput(cloneSource.contractionOutputAddress, newEdge.contractionOutputAddress)
                    } else {
                        for (addr in longArrayOf(
                            newEdge.contractionInputAddress,
                            newEdge.contractionOutputAddress
                        )) {
                            val n = Brain.Node()
                            brain.nodes.add(n)
                            brain.bindings.put(addr, n)
                        }
                    }
                }
            }
        }
        if (anatomy.edges.isNotEmpty() && random.nextBoolean(config.edgeRemoveChance)) {
            val edgeToRemove =
                anatomy.edges[random.nextInt(anatomy.edges.size)]
            anatomy.edges.remove(edgeToRemove)
            brain.removeNode(edgeToRemove.contractionInputAddress)
            brain.removeNode(edgeToRemove.contractionOutputAddress)
        }
    }
}
