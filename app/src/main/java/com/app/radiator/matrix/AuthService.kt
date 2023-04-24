package com.app.radiator.matrix

import org.matrix.rustcomponents.sdk.SessionVerificationControllerDelegate
import org.matrix.rustcomponents.sdk.SessionVerificationEmoji


class SessionVerification(client: MatrixClient) : SessionVerificationControllerDelegate {
  private val ffiInterface = client.client.getSessionVerificationController()

  init {
    ffiInterface.setDelegate(this)
  }

  fun requestVerification() {
    ffiInterface.requestVerification()
  }

  fun startVerification() {
    ffiInterface.startSasVerification()
  }

  override fun didAcceptVerificationRequest() {
    TODO("Not yet implemented")
  }

  override fun didCancel() {
    TODO("Not yet implemented")
  }

  override fun didFail() {
    TODO("Not yet implemented")
  }

  override fun didFinish() {
    TODO("Not yet implemented")
  }

  override fun didReceiveVerificationData(data: List<SessionVerificationEmoji>) {
    TODO("Not yet implemented")
  }

  override fun didStartSasVerification() {
    TODO("Not yet implemented")
  }

}