package com.clearscreen.app.render

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/** The one full-screen quad every renderer in this app draws into clip space. */
object QuadGeometry {
    const val FLOATS_PER_VERTEX = 4 // x, y, u, v
    const val STRIDE_BYTES = FLOATS_PER_VERTEX * 4

    fun makeBuffer(): FloatBuffer {
        // Triangle strip covering clip space [-1,1]; texcoords [0,1] with (0,0) at the top-left,
        // matching the pixel-space convention CropTransform assumes. See CameraTextureRenderer.
        val data = floatArrayOf(
            -1f, -1f, 0f, 1f,
            1f, -1f, 1f, 1f,
            -1f, 1f, 0f, 0f,
            1f, 1f, 1f, 0f
        )
        return ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(data); position(0) }
    }
}
