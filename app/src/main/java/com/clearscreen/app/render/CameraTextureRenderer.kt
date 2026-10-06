package com.clearscreen.app.render

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Draws a full-screen quad sampling an external-OES camera texture, applying the calibration
 * crop/zoom/offset/rotation matrix, the SurfaceTexture's own sample-space transform, a brightness
 * (dim overlay) multiplier, and an optional cheap 9-tap box blur.
 *
 * Must only be touched from the GL render thread that owns the current EGL context.
 */
class CameraTextureRenderer {

    val textureId: Int = createExternalTexture()
    val surfaceTexture: SurfaceTexture = SurfaceTexture(textureId)

    private var program = 0
    private var aPositionLoc = 0
    private var aTexCoordLoc = 0
    private var uCropMatrixLoc = 0
    private var uStMatrixLoc = 0
    private var uTextureLoc = 0
    private var uBrightnessLoc = 0
    private var uBlurEnabledLoc = 0
    private var uTexelSizeLoc = 0

    private val stMatrix = FloatArray(16)
    private val quadVertices: FloatBuffer = makeQuadBuffer()

    init {
        program = ShaderUtil.buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        aPositionLoc = GLES20.glGetAttribLocation(program, "aPosition")
        aTexCoordLoc = GLES20.glGetAttribLocation(program, "aTexCoord")
        uCropMatrixLoc = GLES20.glGetUniformLocation(program, "uCropMatrix")
        uStMatrixLoc = GLES20.glGetUniformLocation(program, "uSTMatrix")
        uTextureLoc = GLES20.glGetUniformLocation(program, "uTexture")
        uBrightnessLoc = GLES20.glGetUniformLocation(program, "uBrightness")
        uBlurEnabledLoc = GLES20.glGetUniformLocation(program, "uBlurEnabled")
        uTexelSizeLoc = GLES20.glGetUniformLocation(program, "uTexelSize")
    }

    /** Call once per onFrameAvailable to drain the camera's buffer queue, whether or not we render this tick. */
    fun updateTexImage() {
        surfaceTexture.updateTexImage()
        surfaceTexture.getTransformMatrix(stMatrix)
    }

    fun draw(
        viewWidth: Int,
        viewHeight: Int,
        frameWidth: Int,
        frameHeight: Int,
        sensorOrientationDegrees: Int,
        displayRotationDegrees: Int,
        zoom: Float,
        offsetXFraction: Float,
        offsetYFraction: Float,
        brightness: Float,
        blurEnabled: Boolean
    ) {
        GLES20.glViewport(0, 0, viewWidth, viewHeight)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        GLES20.glUseProgram(program)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(uTextureLoc, 0)

        val cropMatrix = CropTransform.computeCropMatrix(
            nativeFrameWidth = frameWidth,
            nativeFrameHeight = frameHeight,
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            sensorOrientationDegrees = sensorOrientationDegrees,
            displayRotationDegrees = displayRotationDegrees,
            zoom = zoom,
            offsetXFraction = offsetXFraction,
            offsetYFraction = offsetYFraction
        )
        GLES20.glUniformMatrix4fv(uCropMatrixLoc, 1, false, cropMatrix, 0)
        GLES20.glUniformMatrix4fv(uStMatrixLoc, 1, false, stMatrix, 0)
        GLES20.glUniform1f(uBrightnessLoc, brightness)
        GLES20.glUniform1f(uBlurEnabledLoc, if (blurEnabled) 1f else 0f)
        GLES20.glUniform2f(
            uTexelSizeLoc,
            if (frameWidth > 0) 1f / frameWidth else 0f,
            if (frameHeight > 0) 1f / frameHeight else 0f
        )

        quadVertices.position(0)
        GLES20.glVertexAttribPointer(aPositionLoc, 2, GLES20.GL_FLOAT, false, STRIDE, quadVertices)
        GLES20.glEnableVertexAttribArray(aPositionLoc)

        quadVertices.position(2)
        GLES20.glVertexAttribPointer(aTexCoordLoc, 2, GLES20.GL_FLOAT, false, STRIDE, quadVertices)
        GLES20.glEnableVertexAttribArray(aTexCoordLoc)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        ShaderUtil.checkGlError("draw")
    }

    fun release() {
        surfaceTexture.release()
        GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
        if (program != 0) {
            GLES20.glDeleteProgram(program)
            program = 0
        }
    }

    private fun createExternalTexture(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        val id = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, id)
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE
        )
        return id
    }

    companion object {
        private const val FLOATS_PER_VERTEX = 4 // x, y, u, v
        private const val STRIDE = FLOATS_PER_VERTEX * 4 // bytes

        private fun makeQuadBuffer(): FloatBuffer {
            // Triangle strip covering clip space [-1,1], texcoords [0,1] (origin top-left of the quad;
            // the actual sample-space mapping is handled by uCropMatrix / uSTMatrix).
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

        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            uniform mat4 uCropMatrix;
            uniform mat4 uSTMatrix;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vec4 cropped = uCropMatrix * vec4(aTexCoord, 0.0, 1.0);
                vTexCoord = (uSTMatrix * cropped).xy;
            }
        """

        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTexCoord;
            uniform samplerExternalOES uTexture;
            uniform float uBrightness;
            uniform float uBlurEnabled;
            uniform vec2 uTexelSize;
            void main() {
                vec4 color;
                if (uBlurEnabled > 0.5) {
                    vec4 sum = vec4(0.0);
                    sum += texture2D(uTexture, vTexCoord + vec2(-uTexelSize.x, -uTexelSize.y));
                    sum += texture2D(uTexture, vTexCoord + vec2( 0.0,          -uTexelSize.y));
                    sum += texture2D(uTexture, vTexCoord + vec2( uTexelSize.x, -uTexelSize.y));
                    sum += texture2D(uTexture, vTexCoord + vec2(-uTexelSize.x,  0.0));
                    sum += texture2D(uTexture, vTexCoord);
                    sum += texture2D(uTexture, vTexCoord + vec2( uTexelSize.x,  0.0));
                    sum += texture2D(uTexture, vTexCoord + vec2(-uTexelSize.x,  uTexelSize.y));
                    sum += texture2D(uTexture, vTexCoord + vec2( 0.0,           uTexelSize.y));
                    sum += texture2D(uTexture, vTexCoord + vec2( uTexelSize.x,  uTexelSize.y));
                    color = sum / 9.0;
                } else {
                    color = texture2D(uTexture, vTexCoord);
                }
                gl_FragColor = vec4(color.rgb * uBrightness, 1.0);
            }
        """
    }
}
