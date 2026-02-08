package com.example.worker1

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.CompoundBarcodeView

class ScannerActivity : AppCompatActivity() {

    private lateinit var barcodeView: CompoundBarcodeView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scanner)

        barcodeView = findViewById(R.id.barcodeScanner)

        // 1. 카메라 권한이 있는지 확인합니다.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            // 권한이 없으면 사용자에게 팝업을 띄워 요청합니다.
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1001)
        } else {
            // 권한이 이미 있다면 스캔을 시작합니다.
            initQRScanner()
        }
    }

    private fun initQRScanner() {
        barcodeView.decodeContinuous(object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult?) {
                result?.let {
                    val scannedText = it.text
                    if (scannedText == "https://m.site.naver.com/1Y6Oa") {
                        val intent = Intent(this@ScannerActivity, MainActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                }
            }
            override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>?) {}
        })
    }

    // 2. 권한 요청 팝업에서 사용자가 '허용' 혹은 '거부'를 눌렀을 때 실행되는 함수
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 사용자가 허용했을 때
                initQRScanner()
                barcodeView.resume() // 카메라 즉시 실행
            } else {
                // 사용자가 거부했을 때
                Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                finish() // 화면 종료
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 권한이 있을 때만 resume을 호출하는 것이 안전합니다.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            barcodeView.resume()
        }
    }

    override fun onPause() {
        super.onPause()
        barcodeView.pause()
    }
}