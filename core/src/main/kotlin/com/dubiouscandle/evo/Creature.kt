package com.dubiouscandle.evo

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.*
import com.badlogic.gdx.physics.box2d.joints.DistanceJoint
import com.badlogic.gdx.physics.box2d.joints.DistanceJointDef
import com.badlogic.gdx.utils.FloatArray
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

class Creature(val genome: Genome, val world: World, startX: Float, startY: Float) {
    var fitness = 0

    companion object {
        internal val BIAS_ADDRESS = InnovationTracker.getNextAddress()
        private const val CATEGORY_GROUND: Short = 0x0001
        private const val CATEGORY_CREATURE: Short = 0x0002
    }

    val processor = genome.brain.processor()
    val bodies = ArrayList<Body>()
    val bodyEquilibriumAngle = FloatArray()
    val joints = ArrayList<DistanceJoint>()
    val baseLengths = ArrayList<Float>()
    val edges = ArrayList<Anatomy.Edge>()
    val nodeMap = HashMap<Anatomy.Node, Body>()
    val sensorDataMap = HashMap<Anatomy.Sensor, IntArray>()

    val cachedPosX = kotlin.FloatArray(genome.anatomy.nodes.size)
    val cachedPosY = kotlin.FloatArray(genome.anatomy.nodes.size)
    val cachedCenterX = kotlin.FloatArray(genome.anatomy.nodes.size)
    val cachedCenterY = kotlin.FloatArray(genome.anatomy.nodes.size)
    val cachedAngle = kotlin.FloatArray(genome.anatomy.nodes.size)
    val cachedVelX = kotlin.FloatArray(genome.anatomy.nodes.size)
    val cachedVelY = kotlin.FloatArray(genome.anatomy.nodes.size)

    init {
        processor.clear()

        for (node in genome.anatomy.nodes) {
            val def = BodyDef()
            def.type = BodyDef.BodyType.DynamicBody
            def.position.set(startX + node.x, startY + node.y)
            val body = world.createBody(def)
            body.linearDamping = 0f
            val shape = CircleShape()
            shape.radius = sqrt(node.mass) * 0.4f

            val fixtureDef = FixtureDef()
            fixtureDef.shape = shape
            fixtureDef.friction = node.friction
            fixtureDef.restitution = node.restitution
            fixtureDef.density = 1f
            fixtureDef.filter.categoryBits = CATEGORY_CREATURE
            fixtureDef.filter.maskBits = CATEGORY_GROUND

            val mainFixture = body.createFixture(fixtureDef)
            val bodyContactInfo = IntArray(1)
            mainFixture.userData = bodyContactInfo
            body.userData = bodyContactInfo

            for (sensor in node.sensors) {
                val sensorShape = CircleShape()
                sensorShape.radius = sensor.range
                val sensorFixtureDef = FixtureDef()
                sensorFixtureDef.shape = sensorShape
                sensorFixtureDef.isSensor = true
                sensorFixtureDef.filter.categoryBits = CATEGORY_CREATURE
                sensorFixtureDef.filter.maskBits = CATEGORY_GROUND
                val sensorFixture = body.createFixture(sensorFixtureDef)
                val sensorContactInfo = IntArray(1)
                sensorFixture.userData = sensorContactInfo
                sensorDataMap[sensor] = sensorContactInfo
                sensorShape.dispose()
            }

            val massData = MassData()
            massData.mass = node.mass
            massData.I = body.inertia
            body.massData = massData

            shape.dispose()
            bodies.add(body)
            nodeMap[node] = body
        }

        for (edge in genome.anatomy.edges) {
            val bA = nodeMap[edge.nodeA]
            val bB = nodeMap[edge.nodeB]
            if (bA == null || bB == null) {
                throw IllegalStateException("No node for edge ${edge}")
            }
            val def = DistanceJointDef()
            def.dampingRatio = edge.damping
            def.frequencyHz = edge.stiffness
            def.bodyA = bA
            def.bodyB = bB
            def.localAnchorA.set(0f, 0f)
            def.localAnchorB.set(0f, 0f)

            val restLen = Vector2.dst(edge.nodeA.x, edge.nodeA.y, edge.nodeB.x, edge.nodeB.y)
            def.length = restLen

            val joint = world.createJoint(def) as DistanceJoint
            joints.add(joint)
            baseLengths.add(restLen)
            edges.add(edge)
        }

        for (i in 0 until genome.anatomy.nodes.size) {
            val nodeA = genome.anatomy.nodes[i]
            val body = bodies[i]
            var sumX = 0f
            var sumY = 0f
            for (edge in genome.anatomy.edges) {
                if (edge.nodeA == nodeA || edge.nodeB == nodeA) {
                    val otherNode = if (edge.nodeA == nodeA) edge.nodeB else edge.nodeA
                    val dx = otherNode.x - nodeA.x
                    val dy = otherNode.y - nodeA.y
                    val len = sqrt(dx * dx + dy * dy)
                    if (len > 0.0001f) {
                        sumX += dx / len
                        sumY += dy / len
                    }
                }
            }
            val angle = atan2(sumY, sumX)
            bodyEquilibriumAngle.add(angle)
            body.setTransform(body.position, angle)
        }
    }


    fun update() {
        for (i in 0 until bodies.size) {
            val body = bodies[i]
            val pos = body.position
            cachedPosX[i] = pos.x
            cachedPosY[i] = pos.y
            val center = body.worldCenter
            cachedCenterX[i] = center.x
            cachedCenterY[i] = center.y
            cachedAngle[i] = body.angle
            val vel = body.linearVelocity
            cachedVelX[i] = vel.x
            cachedVelY[i] = vel.y
        }

        for (i in 0 until bodies.size) {
            val body = bodies[i]
            val nodeA = genome.anatomy.nodes[i]
            var currentSumX = 0f
            var currentSumY = 0f
            for (edge in genome.anatomy.edges) {
                if (edge.nodeA == nodeA || edge.nodeB == nodeA) {
                    val otherNode = if (edge.nodeA == nodeA) edge.nodeB else edge.nodeA
                    val otherIndex = genome.anatomy.nodes.indexOf(otherNode)
                    val dx = cachedPosX[otherIndex] - cachedPosX[i]
                    val dy = cachedPosY[otherIndex] - cachedPosY[i]
                    val len = sqrt(dx * dx + dy * dy)
                    if (len > 0.0001f) {
                        currentSumX += dx / len
                        currentSumY += dy / len
                    }
                }
            }
            val currentTargetAngle = atan2(currentSumY, currentSumX)
            var angleDiff = (currentTargetAngle - cachedAngle[i]) % (2f * PI.toFloat())
            if (angleDiff > PI.toFloat()) angleDiff -= 2f * PI.toFloat()
            if (angleDiff < -PI.toFloat()) angleDiff += 2f * PI.toFloat()
            body.applyTorque(angleDiff * body.inertia * 6000f, true)
        }

        var comX = 0f
        var comY = 0f
        for (i in 0 until genome.anatomy.nodes.size) {
            comX += cachedCenterX[i]
            comY += cachedCenterY[i]
        }
        comX /= genome.anatomy.nodes.size
        comY /= genome.anatomy.nodes.size
        processor.setInput(BIAS_ADDRESS, 1f)
        for (i in 0 until genome.anatomy.nodes.size) {
            val node = genome.anatomy.nodes[i]
            val body = bodies[i]
            processor.setInput(node.xVelocityAddress, cachedVelX[i])
            processor.setInput(node.yVelocityAddress, cachedVelY[i])
            processor.setInput(node.xOffsetAddress, cachedPosX[i] - comX)
            processor.setInput(node.yOffsetAddress, cachedPosY[i] - comY)
            processor.setInput(node.contactAddress, if ((body.userData as IntArray)[0] > 0) 1f else 0f)

            for (sensor in node.sensors) {
                processor.setInput(sensor.inputAddress, if (sensorDataMap[sensor]!![0] > 0) 1f else 0f)
            }
        }
        for (i in 0 until genome.anatomy.edges.size) {
            val edge = edges[i]
            val indexA = genome.anatomy.nodes.indexOf(edge.nodeA)
            val indexB = genome.anatomy.nodes.indexOf(edge.nodeB)
            val dx = cachedCenterX[indexB] - cachedCenterX[indexA]
            val dy = cachedCenterY[indexB] - cachedCenterY[indexA]
            val dist = sqrt(dx * dx + dy * dy)
            processor.setInput(edge.contractionInputAddress, dist)
        }

        processor.step()

        for (i in 0 until edges.size) {
            val edge = edges[i]
            val joint = joints[i]
            val restLen = baseLengths[i]
            val activation = processor.getOutput(edge.contractionOutputAddress)

            val distance = restLen * (1f + activation * (1f - edge.compressionRatio))
            joint.length = maxOf(0.0001f, distance)

            joint.bodyA.isAwake = true
            joint.bodyB.isAwake = true
        }
    }

    fun destroy() {
        for (joint in joints) world.destroyJoint(joint)
        for (body in bodies) world.destroyBody(body)
    }
}
