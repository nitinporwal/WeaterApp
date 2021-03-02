package com.example.weatherapp

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

object Constants {

  fun isNetworkAvailable(context: Context): Boolean {
    val connectivityManager =
      context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      val network = connectivityManager.activeNetwork ?: return false
      val activeaNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false

      return when {
        activeaNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
        activeaNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
        activeaNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
        else -> false
      }
    } else {
      val networkInfo = connectivityManager.activeNetworkInfo
      return networkInfo != null && networkInfo.isConnectedOrConnecting
    }

  }

}