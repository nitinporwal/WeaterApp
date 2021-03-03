package com.example.weatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.example.weatherapp.models.WeatherResponse
import com.example.weatherapp.network.WeatherService
import com.google.android.gms.location.*
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import retrofit.*
import kotlinx.android.synthetic.main.activity_main.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

  private var mProgressDialog: Dialog? = null

  private lateinit var mFusedLocationClient: FusedLocationProviderClient
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

    if (!isLocationEnabled()) {
      Toast.makeText(
        this,
        "Your location provider is turned off. Please turn it on.",
        Toast.LENGTH_SHORT
      ).show()
      val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
      startActivity(intent)
    } else {
      Dexter.withActivity(this).withPermissions(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
      ).withListener(object : MultiplePermissionsListener {
        override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
          if (report!!.areAllPermissionsGranted()) {
            requestLocationData()
          }

          if (report.isAnyPermissionPermanentlyDenied) {
            Toast.makeText(
              this@MainActivity,
              "You had denied location permission. Please enable them as it is mandatory for the app to work.",
              Toast.LENGTH_SHORT
            ).show()
          }
        }

        override fun onPermissionRationaleShouldBeShown(
          permissions: MutableList<PermissionRequest>?,
          token: PermissionToken?
        ) {
          showRationalDialogForPermissions()
        }
      }).onSameThread().check()
    }
  }

  @SuppressLint("MissingPermission")
  private fun requestLocationData() {
    val mLocationRequest = LocationRequest()
    mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

    mFusedLocationClient.requestLocationUpdates(
      mLocationRequest, mLocationCallback, Looper.myLooper()
    )
  }

  private val mLocationCallback = object : LocationCallback() {
    override fun onLocationResult(locationResult: LocationResult) {
      val mLastLocation: Location = locationResult.lastLocation
      val latitude = mLastLocation.latitude
      val longitude = mLastLocation.longitude
      Log.i("Current Latitude", "$latitude")
      Log.i("Current Longitude", "$longitude")
      getLocationWeatherDetails(latitude, longitude)
    }
  }

  private fun getLocationWeatherDetails(latitude: Double, longitude: Double) {
    if (Constants.isNetworkAvailable(this)) {
      val retrofit: Retrofit = Retrofit.Builder().baseUrl(Constants.BASE_URL)
        .addConverterFactory(GsonConverterFactory.create()).build()
      val service: WeatherService = retrofit.create<WeatherService>(WeatherService::class.java)
      val listCall: Call<WeatherResponse> =
        service.getWeather(latitude, longitude, Constants.METRIC_UNIT, ApiKey.APP_ID)
      showCustomProgressDialog()
      listCall.enqueue(object : Callback<WeatherResponse> {
        override fun onResponse(response: Response<WeatherResponse>?, retrofit: Retrofit?) {
          if (response!!.isSuccess) {
            hideProgressDialog()
            val weatherList: WeatherResponse = response.body()
            setupUI(weatherList)
            Log.i("Response Result", "$weatherList")
          } else {
            val rc = response.code()
            when (rc) {
              400 -> Log.e("Error 400", "Bad Connection")
              404 -> Log.e("Error 404", "Not Found")
              else -> Log.e("Error", "Generic Error")
            }
          }
        }

        override fun onFailure(t: Throwable?) {
          hideProgressDialog()
          Log.e("Errorrrrrr", t!!.message.toString())
        }

      })
    } else {
      Toast.makeText(this, "No internet connection available", Toast.LENGTH_SHORT).show()
    }
  }

  public fun showRationalDialogForPermissions() {
    AlertDialog.Builder(this)
      .setMessage("It looks like you have turned off permissions required for this feature. It can be enabled under Application Settings")
      .setPositiveButton("GO TO SETTINGS") { _, _ ->
        try {
          val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
          val uri = Uri.fromParts("package", packageName, null)
          intent.data = uri
          startActivity(intent)
        } catch (e: ActivityNotFoundException) {
          e.printStackTrace()
        }
      }.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }.show()
  }

  private fun isLocationEnabled(): Boolean {
    val locationManager: LocationManager =
      getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
      LocationManager.NETWORK_PROVIDER
    )
  }

  private fun showCustomProgressDialog() {
    mProgressDialog = Dialog(this)
    mProgressDialog!!.setContentView(R.layout.dialog_custom_progress)
    mProgressDialog!!.show()
  }

  private fun hideProgressDialog() {
    if (mProgressDialog != null) {
      mProgressDialog!!.dismiss()
    }
  }

  private fun setupUI(weatherList: WeatherResponse) {
    for (i in weatherList.weather.indices) {
      Log.i("Weather Name", weatherList.weather.toString())
      tv_main.text = weatherList.weather[i].main
      tv_main_description.text = weatherList.weather[i].description
      tv_temp.text =
        weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())
      tv_humidity.text = weatherList.main.humidity.toString() + " per cent"
      tv_min.text = weatherList.main.temp_min.toString() + " min"
      tv_max.text = weatherList.main.temp_max.toString() + " max"
      tv_speed.text = weatherList.wind.speed.toString()
      tv_name.text = weatherList.name
      tv_country.text = weatherList.sys.country

      tv_sunrise_time.text = unixTime(weatherList.sys.sunrise)
      tv_sunset_time.text = unixTime(weatherList.sys.sunset)
    }
  }

  private fun getUnit(valu: String): String {
    var value = "°C"
    if ("US" == valu || "LR" == valu || "MM" == valu) {
      value = "°F"
    }
    return value
  }

  private fun unixTime(timex: Long): String? {
    val date = Date(timex*1000L)
    val sdf = SimpleDateFormat("HH:mm", Locale.UK)
    sdf.timeZone = TimeZone.getDefault()
    return sdf.format(date)
  }
}