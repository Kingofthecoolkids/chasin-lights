package com.clearscreen.app.render

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import java.nio.FloatBuffer

/**
 * Draws a single plain 2D bitmap, cover-fit to the view -- used for the "effect off" state, to
 * show a snapshot of whatever wallpaper was active before ClearScreen took over. Reuses
 * [CropTransform] with rotation/zoom/offset neutralized, since cover-fit scaling is exactly what
 * that matrix already computes.
 */
class StaticBitmapRenderer {

    private var program = 0
    private var aPositionLoc = 0
    private var aTexCoordLoc = 0
    private var uCropMatrixLoc = 0
    private var uTextureLoc = 0
    private val textureId: Int = createTexture()
    private val quadVertices: FloatBuffer = QuadGeometry.makeBuffer()

    var bitmapWidth = 0
        private set
    var bitmapHeight = 0
        private set
    var hasBitmap = false
        private set

    init {
        program = ShaderUtil.buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        aPositionLoc = GLES20.glGetAttribLocation(program, "aPosition")
        aTexCoordLoc = GLES20.glGetAttribLocation(program, "aTexCoord")
        uCropMatrixLoc = GLES20.glGetUniformLocation(program, "uCropMatrix")
        uTextureLoc = GLES20.glGetUniformLocation(program, "uTexture")
    }

    fun setBitmap(bitmap: Bitmap) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        bitmapWidth = bitmap.width
        bitmapHeight = bitmap.height
        hasBitmap = true
    }

    fun draw(viewWidth: Int, viewHeight: Int) {
        if (!hasBitmap) return
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(uTextureLoc, 0)

        val matrix = CropTransform.computeCropMatrix(
            nativeFrameWidth = bitmapWidth,
            nativeFrameHeight = bitmapHeight,
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            sensorOrientationDegrees = 0,
            displayRotationDegrees = 0,
            zoom = 1f,
            offsetXFraction = 0f,
            offsetYFraction = 0f
        )
        GLES20.glUniformMatrix4fv(uCropMatrixLoc, 1, false, matrix, 0)

        quadVertices.position(0)
        GLES20.glVertexAttribPointer(aPositionLoc, 2, GLES20.GL_FLOAT, false, QuadGeometry.STRIDE_BYTES, quadVertices)
        GLES20.glEnableVertexAttribArray(aPositionLoc)

        quadVertices.position(2)
        GLES20.glVertexAttribPointer(aTexCoordLoc, 2, GLES20.GL_FLOAT, false, QuadGeometry.STRIDE_BYTES, quadVertices)
        GLES20.glEnableVertexAttribArray(aTexCoordLoc)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    fun release() {
        GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
        if (program != 0) {
            GLES20.glDeleteProgram(program)
            program = 0
        }
    }

    private fun createTexture(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        val id = textures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return id
    }

    companion object {
        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            uniform mat4 uCropMatrix;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = (uCropMatrix * vec4(aTexCoord, 0.0, 1.0)).xy;
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D uTexture;
            void main() {
                gl_FragColor = texture2D(uTexture, vTexCoord);
            }
        """
    }
}
