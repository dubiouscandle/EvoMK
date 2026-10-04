package com.dubiouscandle.evo

import com.badlogic.gdx.utils.LongMap
import kotlin.math.tanh

class Brain {
    val connections = ArrayList<Connection>()
    val bindings = LongMap<Node>()
    val nodes = ArrayList<Node>()

    class Connection(
        val from: Node,
        val to: Node,
        var weight: Float,
        val innovationId: Long = InnovationTracker.getNextAddress()
    )

    @JvmInline
    value class OutputId(val address: Long)

    class Node(
        var bias: Float = 0f,
        val innovationId: Long = InnovationTracker.getNextAddress()
    )

    fun processor(): Processor = Processor(this)

    class Processor(brain: Brain) {
        private val nodeCount = brain.nodes.size
        private val state = FloatArray(nodeCount)
        private val nextState = FloatArray(nodeCount)
        private val biases = FloatArray(nodeCount)
        private val addressToIndex = LongMap<Int>()
        private val fromIndices = IntArray(brain.connections.size)
        private val toIndices = IntArray(brain.connections.size)
        private val weights = FloatArray(brain.connections.size)

        init {
            val nodeToIndex = java.util.HashMap<Node, Int>()
            for (i in 0 until nodeCount) {
                val node = brain.nodes[i]
                nodeToIndex[node] = i
                biases[i] = node.bias
            }

            val iter = brain.bindings.entries().iterator()
            while (iter.hasNext()) {
                val entry = iter.next()
                val idx = nodeToIndex[entry.value]
                if (idx != null) {
                    addressToIndex.put(entry.key, idx)
                }
            }

            for (i in 0 until brain.connections.size) {
                val conn = brain.connections[i]
                fromIndices[i] = nodeToIndex[conn.from]!!
                toIndices[i] = nodeToIndex[conn.to]!!
                weights[i] = conn.weight
            }
        }

        fun clear() {
            for (i in 0 until nodeCount) {
                state[i] = 0f
                nextState[i] = 0f
            }
        }

        fun setInput(address: Long, value: Float) {
            val idx = addressToIndex.get(address, -1)
            if (idx != -1) {
                state[idx] = value
            }
        }

        fun getOutput(address: Long): Float {
            val idx = addressToIndex.get(address, -1)
            return if (idx != -1) state[idx] else 0f
        }

        fun step() {
            System.arraycopy(biases, 0, nextState, 0, nodeCount)
            for (i in 0 until weights.size) {
                nextState[toIndices[i]] += state[fromIndices[i]] * weights[i]
            }
            for (i in 0 until nodeCount) {
                state[i] = tanh(nextState[i])
            }
        }
    }

    fun mergeNodes(idMaster: Long, idVictim: Long) {
        val nodeM = bindings.get(idMaster) ?: throw IllegalArgumentException("Node $idMaster does not exist.")
        val nodeV = bindings.get(idVictim) ?: throw IllegalArgumentException("Node $idVictim does not exist.")

        if (idMaster == idVictim) return

        nodeM.bias += nodeV.bias

        val connectionMap = HashMap<Pair<Node, Node>, Float>()

        for (conn in connections) {
            val newFrom = if (conn.from === nodeV) nodeM else conn.from
            val newTo = if (conn.to === nodeV) nodeM else conn.to

            val endpoints = Pair(newFrom, newTo)
            val currentWeight = connectionMap.getOrDefault(endpoints, 0f)

            connectionMap[endpoints] = currentWeight + conn.weight
        }

        connections.clear()
        for ((endpoints, weight) in connectionMap) {
            connections.add(Connection(endpoints.first, endpoints.second, weight))
        }

        bindings.remove(idVictim)
        nodes.remove(nodeV)
    }

    fun cloneOutput(oldId: Long, newId: Long) {
        val oldNode = bindings.get(oldId) ?: throw IllegalArgumentException("Old node $oldId does not exist.")
        val newNode = Node()

        newNode.bias = oldNode.bias * 0.5f
        oldNode.bias *= 0.5f

        nodes.add(newNode)
        bindings.put(newId, newNode)

        val newConnections = ArrayList<Connection>()
        for (conn in connections) {
            if (conn.to === oldNode) {
                conn.weight *= 0.5f
                newConnections.add(Connection(conn.from, newNode, conn.weight))
            }
        }

        connections.addAll(newConnections)
    }

    fun cloneInputToMany(oldId: Long, newIds: List<Long>) {
        val oldNode = bindings.get(oldId) ?: throw IllegalArgumentException("Old node $oldId does not exist.")
        val fraction = 1f / newIds.size
        val newConnections = ArrayList<Connection>()
        for (newId in newIds) {
            val newNode = Node()
            nodes.add(newNode)
            bindings.put(newId, newNode)
            for (conn in connections) {
                if (conn.from === oldNode) {
                    newConnections.add(Connection(newNode, conn.to, conn.weight * fraction))
                }
            }
        }
        connections.addAll(newConnections)
    }

    fun cloneOutputToMany(oldId: Long, newIds: List<Long>) {
        val oldNode = bindings.get(oldId) ?: throw IllegalArgumentException("Old node $oldId does not exist.")
        val fraction = 1f / newIds.size
        val newConnections = ArrayList<Connection>()
        for (newId in newIds) {
            val newNode = Node()
            newNode.bias = oldNode.bias * fraction
            nodes.add(newNode)
            bindings.put(newId, newNode)
            for (conn in connections) {
                if (conn.to === oldNode) {
                    newConnections.add(Connection(conn.from, newNode, conn.weight * fraction))
                }
            }
        }
        connections.addAll(newConnections)
    }
    // utility function so you don't mess it up
    fun removeNode(id: Long) {
        val nodeToRemove = bindings.remove(id) ?: return
        nodes.remove(nodeToRemove)
        connections.removeAll { it.from === nodeToRemove || it.to === nodeToRemove }
    }

    fun cloneInput(oldId: Long, newId: Long) {
        val oldNode = bindings.get(oldId) ?: throw IllegalArgumentException("Old node $oldId does not exist.")
        val newNode = Node()

        nodes.add(newNode)
        bindings.put(newId, newNode)

        val newConnections = ArrayList<Connection>()
        for (conn in connections) {
            if (conn.from === oldNode) {
                conn.weight *= 0.5f
                newConnections.add(Connection(newNode, conn.to, conn.weight))
            }
        }

        connections.addAll(newConnections)
    }
}
