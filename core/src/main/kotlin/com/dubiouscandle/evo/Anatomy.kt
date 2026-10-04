package com.dubiouscandle.evo

class Anatomy() {
    val nodes = ArrayList<Node>()
    val edges = ArrayList<Edge>()

    constructor(anatomy: Anatomy) : this() {
        val nodeMapOldToNew = HashMap<Node, Node>()

        for (oldNode in anatomy.nodes) {
            val newNode = Node(
                oldNode.x, oldNode.y, oldNode.mass, oldNode.friction,
                oldNode.restitution,
                oldNode.xOffsetAddress,
                oldNode.yOffsetAddress,
                oldNode.xVelocityAddress,
                oldNode.yVelocityAddress,
                oldNode.contactAddress,
                ArrayList()
            )
            for (sensor in oldNode.sensors) {
                newNode.sensors.add(Sensor(sensor.range, sensor.inputAddress))
            }
            nodeMapOldToNew[oldNode] = newNode
            nodes.add(newNode)
        }
        for (oldEdge in anatomy.edges) {
            val newEdge = Edge(
                nodeMapOldToNew[oldEdge.nodeA]!!, nodeMapOldToNew[oldEdge.nodeB]!!,
                oldEdge.stiffness,
                oldEdge.damping,
                oldEdge.compressionRatio,
                oldEdge.contractionInputAddress,
                oldEdge.contractionOutputAddress,
            )
            edges.add(newEdge)
        }
    }

    class Node(
        var x: Float,
        var y: Float,
        var mass: Float,
        var friction: Float,
        var restitution: Float,
        var xOffsetAddress: Long = InnovationTracker.getNextAddress(),
        var yOffsetAddress: Long = InnovationTracker.getNextAddress(),
        var xVelocityAddress: Long = InnovationTracker.getNextAddress(),
        var yVelocityAddress: Long = InnovationTracker.getNextAddress(),
        var contactAddress: Long = InnovationTracker.getNextAddress(),
        val sensors: ArrayList<Sensor> = ArrayList()
    )

    class Edge(
        var nodeA: Node,
        var nodeB: Node,
        var stiffness: Float,
        var damping: Float,
        var compressionRatio: Float,
        var contractionInputAddress: Long = InnovationTracker.getNextAddress(),
        var contractionOutputAddress: Long = InnovationTracker.getNextAddress(),
    )

    class Sensor(
        var range: Float,
        var inputAddress: Long = InnovationTracker.getNextAddress(),
    )
}
