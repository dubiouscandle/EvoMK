package com.dubiouscandle.evo

class Genome(val anatomy: Anatomy, val brain: Brain) {
    fun deepClone(): Genome {
        val newAnatomy = Anatomy(anatomy)
        val newBrain = Brain()
        val nodeMap = HashMap<Brain.Node, Brain.Node>()

        for (n in brain.nodes) {
            val nn = Brain.Node(n.bias, n.innovationId)
            newBrain.nodes.add(nn)
            nodeMap[n] = nn
        }
        val iter = brain.bindings.entries().iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            newBrain.bindings.put(entry.key, nodeMap[entry.value]!!)
        }
        for (c in brain.connections) {
            newBrain.connections.add(Brain.Connection(nodeMap[c.from]!!, nodeMap[c.to]!!, c.weight, c.innovationId))
        }

        return Genome(newAnatomy, newBrain)
    }
}
