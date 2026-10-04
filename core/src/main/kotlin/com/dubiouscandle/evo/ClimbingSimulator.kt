package com.dubiouscandle.evo

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.physics.box2d.BodyDef
import com.badlogic.gdx.physics.box2d.FixtureDef
import com.badlogic.gdx.physics.box2d.PolygonShape
import kotlin.math.min

class ClimbingSimulator(
    genome: Genome,
    val stepWidth: Float = 2f,
    val stepHeight: Float = 1f,
    val angle: Float = 0f
) : SoloSimulator(genome) {
    init {
        val groundDef = BodyDef()
        groundDef.position.set(0f, 0f)
        val groundBody = world.createBody(groundDef)

        val fixtureDef = FixtureDef()
        fixtureDef.friction = 1f
        fixtureDef.filter.categoryBits = 0x0001
        fixtureDef.filter.maskBits = -1

        for (i in -20..200) {
            val shape = PolygonShape()
            val cx = i * stepWidth + stepWidth / 2f
            val cy = (if (i > 0) i * stepHeight else 0f) - 50f
            shape.setAsBox(stepWidth / 2f, 50f, Vector2(cx, cy), 0f)

            fixtureDef.shape = shape
            groundBody.createFixture(fixtureDef)
            shape.dispose()
        }

        groundBody.setTransform(groundBody.position, angle)

        var lowestY = Float.MAX_VALUE
        for (body in creature.bodies) {
            for (fixture in body.fixtureList) {
                lowestY = min(lowestY, body.position.y - fixture.shape.radius)
            }
        }
        val shiftY = 1f - lowestY
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
