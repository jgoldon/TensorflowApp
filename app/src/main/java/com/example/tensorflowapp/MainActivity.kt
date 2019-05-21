package com.example.tensorflowapp

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.appcompat.app.AppCompatActivity
import com.example.tensorflowapp.Constants.INPUT_SIZE
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import java.io.FileNotFoundException

class MainActivity : AppCompatActivity() {

    private val pickPhotoRequestCode: Int = 101
    private lateinit var classifier: ImageClassifier
    private lateinit var photoImage: Bitmap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        classifier = ImageClassifier(getAssets())
        fab.setOnClickListener {
            pickImage()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == pickPhotoRequestCode && resultCode == Activity.RESULT_OK)
            try {
                val stream = contentResolver!!.openInputStream(data!!.getData())
                if (::photoImage.isInitialized) photoImage.recycle()
                photoImage = BitmapFactory.decodeStream(stream)
                photoImage = Bitmap.createScaledBitmap(photoImage, INPUT_SIZE, INPUT_SIZE, false)
                contentIV.setImageBitmap(photoImage)
                classifier.recognizeImage(photoImage).subscribeBy(
                    onSuccess = {
                        tagsTV.text = it.toString()
                    }
                )
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
            }
    }


    private fun getImageFromData(data: Intent?): Bitmap? {
        val selectedImage: Uri? = data?.data
        return MediaStore.Images.Media.getBitmap(this.contentResolver, selectedImage)
    }

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        startActivityForResult(intent, pickPhotoRequestCode)
    }

    override fun onDestroy() {
        super.onDestroy()
        classifier.close()
    }
}
