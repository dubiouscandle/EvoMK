package com.dubiouscandle.evo

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.*

abstract class SoloSimulator(
    genome: Genome,
    val world: World = World(Vector2(0f, -9.81f), true)
) : ContactListener {
    val creature: Creature

    init {
        world.setContactListener(this)
        world.setContinuousPhysics(false)
        creature = Creature(genome, world, 0f, 0f)
    }

    fun update(dt: Float = 1f / 60f) {
        world.step(dt, 2, 1)
        creature.update()
    }

    abstract fun getFitness(): Float

    fun dispose() {
        creature.destroy()
        world.dispose()
    }

    override fun beginContact(contact: Contact) {
        val udA = contact.fixtureA.userData
        val udB = contact.fixtureB.userData
        if (udA is IntArray) udA[0]++
        if (udB is IntArray) udB[0]++
    }

    override fun endContact(contact: Contact) {
        val udA = contact.fixtureA.userData
        val udB = contact.fixtureB.userData
        if (udA is IntArray) udA[0]--
        if (udB is IntArray) udB[0]--
    }

    override fun preSolve(contact: Contact, oldManifold: Manifold) {}
    override fun postSolve(contact: Contact, impulse: ContactImpulse) {}
}
