package com.app.radiator.matrix

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.matrix.rustcomponents.sdk.SessionVerificationController
import org.matrix.rustcomponents.sdk.SessionVerificationControllerDelegate
import org.matrix.rustcomponents.sdk.SessionVerificationEmoji

// TODO: introduce UI for this

class SessionVerification(client: MatrixClient) : SessionVerificationControllerDelegate {
  private var ffiInterface: SessionVerificationController
  private var isVerifying = false

  init {
    ffiInterface = client.client.getSessionVerificationController()
    ffiInterface.setDelegate(this)
  }

  fun requestVerification() {
    if(!ffiInterface.isVerified() && !isVerifying) {
      Log.i("Verification", "Requesting verification...")
      isVerifying = true
      ffiInterface.requestVerification()
    } else {
      Log.i("Verification", "already verified!")
    }
  }

  fun startVerification() {
    runBlocking {
      try {
        ffiInterface.startSasVerification()
        Log.i("Verification", "Verification start succeeded")
      } catch (ex: Exception) {
        Log.i("Verification", "Verification Failed $ex")
      }
    }
  }

  override fun didAcceptVerificationRequest() {
    Log.i("Verification", "Verification request accepted")
  }

  override fun didCancel() {
    Log.i("Verification", "Cancelled verification")
  }

  override fun didFail() {
    Log.i("Verification", "Verification failed")
  }

  override fun didFinish() {
    Log.i("Verification", "Verification finished")
  }

  override fun didReceiveVerificationData(data: List<SessionVerificationEmoji>) {
    Log.i("Verification", "Received verification data ${data.joinToString(separator = "; ")}")
    runBlocking {
      try {
        withContext(Dispatchers.IO) {
          ffiInterface.approveVerification()
          Log.i("Verification", "Verification approved.")
        }
      } catch(ex: Exception) {
        Log.i("Verification", "Approve request failed $ex")
      }
    }
  }

  override fun didStartSasVerification() {
    Log.i("Verification", "Verification started")
    startVerification()
  }
}