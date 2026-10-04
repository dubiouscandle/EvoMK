package com.dubiouscandle.evo

import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.FixtureDef
import com.badlogic.gdx.physics.box2d.PolygonShape
import kotlin.math.min

class RunningSimulator(genome: Genome, slope: Float = 0f) : SoloSimulator(genome) {
    init {
        val groundDef = BodyDef()
        groundDef.position.set(0f, -20f)
        val groundBody = world.createBody(groundDef)
        groundBody.setTransform(groundBody.position, slope)
        val groundShape = PolygonShape()
        groundShape.setAsBox(1000f, 10f)

        val fixtureDef = FixtureDef()
        fixtureDef.shape = groundShape
        fixtureDef.friction = 1f
        fixtureDef.filter.categoryBits = 0x0001
        fixtureDef.filter.maskBits = -1

        groundBody.createFixture(fixtureDef)
        groundShape.dispose()

        var lowestY = Float.MAX_VALUE
        for (body in creature.bodies) {
            for (fixture in body.fixtureList) {
                lowestY = min(lowestY, body.position.y - fixture.shape.radius)
            }
        }
        val shiftY = -9f - lowestY
        for (body in creature.bodies) {
            body.setTransform(body.position.x, body.position.y + shiftY, body.angle)
        }
    }

    override fun getFitness(): Float {
        var minX = Float.MAX_VALUE
        for (body in creature.bodies) {
            minX = min(body.worldCenter.x, minX)
        }
        return if (creature.bodies.isNotEmpty()) minX else 0f
    }
}
