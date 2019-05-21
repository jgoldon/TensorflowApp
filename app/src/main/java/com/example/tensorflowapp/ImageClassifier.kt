package com.example.tensorflowapp

import android.content.res.AssetManager
import android.graphics.Bitmap
import com.example.tensorflowapp.Constants.BATCH_SIZE
import com.example.tensorflowapp.Constants.IMG_SIZE_X
import com.example.tensorflowapp.Constants.IMG_SIZE_Y
import com.example.tensorflowapp.Constants.PIXEL_SIZE
import com.example.tensorflowapp.Constants.INPUT_SIZE
import com.example.tensorflowapp.Constants.LABEL_PATH
import com.example.tensorflowapp.Constants.MODEL_PATH
import io.reactivex.Single
import org.tensorflow.lite.Interpreter
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.*

class ImageClassifier constructor(private val assetManager: AssetManager) {

    private var interpreter: Interpreter? = null
    private var labelProb: Array<ByteArray>
    private val labels = Vector<String>()
    private val intValues by lazy { IntArray(INPUT_SIZE * INPUT_SIZE) }
    private var imgData: ByteBuffer

    init {
        try {
            val br = BufferedReader(InputStreamReader(assetManager.open(LABEL_PATH)))
            while (true) {
                val line = br.readLine() ?: break
                labels.add(line)
            }
            br.close()
        } catch (e: IOException) {
            throw RuntimeException("Problem reading label file!", e)
        }
        labelProb = Array(1) { ByteArray(labels.size) }
        imgData = ByteBuffer.allocateDirect(BATCH_SIZE * IMG_SIZE_X * IMG_SIZE_Y * PIXEL_SIZE)
        imgData.order(ByteOrder.nativeOrder())
        try {
            interpreter = Interpreter(loadModelFile(assetManager, MODEL_PATH))
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap) {
        imgData.rewind()
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var pixel = 0
        for (i in 0 until IMG_SIZE_X) {
            for (j in 0 until IMG_SIZE_Y) {
                val value = intValues[pixel++]
                imgData.put((value shr 16 and 0xFF).toByte())
                imgData.put((value shr 8 and 0xFF).toByte())
                imgData.put((value and 0xFF).toByte())
            }
        }
    }

    private fun loadModelFile(assets: AssetManager, modelFilename: String): MappedByteBuffer {
        val fileDescriptor = assets.openFd(modelFilename)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun recognizeImage(bitmap: Bitmap): Single<List<Result>> {
        return Single.just(bitmap).flatMap {
            convertBitmapToByteBuffer(it)
            interpreter!!.run(imgData, labelProb)
            val pq = PriorityQueue<Result>(3,
                Comparator<Result> { lhs, rhs ->
                    java.lang.Float.compare(rhs.confidence!!, lhs.confidence!!)
                })
            for (i in labels.indices) {
                pq.add(Result(if (labels.size > i) labels[i] else "unknown", labelProb[0][i].toFloat()))
            }
            val recognitions = ArrayList<Result>()
            recognitions.addAll(pq.filter { it.confidence!! > 1})
            return@flatMap Single.just(recognitions)
        }
    }

    fun close() {
        interpreter?.close()
    }
}

class Result(val title: String?, val confidence: Float?) {
    override fun toString(): String {
        var resultString = ""
        if (title != null) resultString += title + ": "
        if (confidence != null) resultString += confidence.toString()
        return resultString
    }
}