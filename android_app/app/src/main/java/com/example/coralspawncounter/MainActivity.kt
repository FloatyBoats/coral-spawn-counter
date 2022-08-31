package com.example.coralspawncounter

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import org.opencv.android.OpenCVLoader

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if(OpenCVLoader.initDebug()){
            Log.d("OpenCv Integrated:","true")
        } else{
            Log.d("OpenCv Integrated:","false")
        }
        setContentView(R.layout.activity_main)
    }
}
