package com.edtest.locationsniffer;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    boolean locationLogging, locationStarted;

    Button startLogging, startLocation;
    EditText locationName;
    ListView listView;
    Spinner spinner_fastest, spinner_update;
    TextView headerTextView;

    ArrayList<String> log;
    ArrayAdapter arrayAdapter;

    ScheduledFuture randomTask;
    ScheduledThreadPoolExecutor exec;
    Runnable periodicTask;
    int scanInterval = 500; //milliseconds

    String locationNameString;
    LatLng latLng, lastLatLng;

    private LocationRequest mLocationRequest;
    private LocationCallback locationCallback;

    private long UPDATE_INTERVAL = 10 * 1000;  /* 10 secs */
    private long FASTEST_INTERVAL = 2000; /* 2 sec */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //https://guides.codepath.com/android/Retrieving-Location-with-LocationServices-API
        //https://www.valleyprogramming.com/blog/android-fused-location-provider-api-example-broomfield-co

        locationLogging = false;

        listView = findViewById(R.id.listView);
        locationName = findViewById(R.id.editText);
        headerTextView = findViewById(R.id.headerTextView);
        startLogging = findViewById(R.id.startLogging);
        startLogging.setEnabled(false);
        startLogging.setBackground(ContextCompat.getDrawable(this, R.drawable.button_inactive));
        startLocation = findViewById(R.id.startLocation);
        startLocation.setEnabled(false);
        startLocation.setBackground(ContextCompat.getDrawable(this, R.drawable.button_inactive));

        spinner_fastest = findViewById(R.id.spinner_fastest);
        ArrayAdapter<CharSequence> adapter_fastest = ArrayAdapter.createFromResource(this, R.array.fastest_interval, android.R.layout.simple_spinner_item);
        adapter_fastest.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner_fastest.setAdapter(adapter_fastest);
        spinner_fastest.setSelection(3);

        spinner_update = findViewById(R.id.spinner_update);
        ArrayAdapter<CharSequence> adapter_update = ArrayAdapter.createFromResource(this, R.array.update_interval, android.R.layout.simple_spinner_item);
        adapter_update.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner_update.setAdapter(adapter_update);
        spinner_update.setSelection(0);

        locationNameString = "BLANK";

        log = new ArrayList<>();
        String logHeaders = "time_stamp, location_name, lat, lon, distance_moved, update_interval, fastest_interval";
        headerTextView.setText(logHeaders);
        log.add(0,logHeaders);
        arrayAdapter = new ArrayAdapter(this, R.layout.row_layout, R.id.label, log);
        listView.setAdapter(arrayAdapter);

        periodicTask = () -> {
            try {
                locationLogging = true;
                double d = 0d;
                //get location
                if (latLng != lastLatLng) {
                    d = distance(latLng.latitude, latLng.longitude, lastLatLng.latitude, lastLatLng.longitude);
                }
                lastLatLng = latLng;

                String distanceInFeet = String.format("%.1f",d*5280);
                String latString = String.valueOf(latLng.latitude);
                String lonString = String.valueOf(latLng.longitude);
                String locationLogData = System.currentTimeMillis() + ", " + locationNameString + ", " + latString + ", " + lonString + ", " + distanceInFeet + ", " + UPDATE_INTERVAL + ", " + FASTEST_INTERVAL;

                //add it to the log
                log.add(0, locationLogData);

                //write it to a file
                writeToFile(locationLogData);

                this.runOnUiThread(() -> {
                    arrayAdapter.notifyDataSetChanged();
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        };//periodicTask

        exec = new ScheduledThreadPoolExecutor(1);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                // do work here
                onLocationChanged(locationResult.getLastLocation());
            }
        };

    }//onCreate

    @Override
    protected void onPostResume() {
        super.onPostResume();
        //check all permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            //dont have all permissions
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        } else {
            startLogging.setEnabled(true);
            startLogging.setBackground(ContextCompat.getDrawable(this, R.drawable.button_ready));
            startLocation.setEnabled(true);
            startLocation.setBackground(ContextCompat.getDrawable(this, R.drawable.button_ready));
        }
    }//onPostResume

    @Override
    public void onPause() {
        super.onPause();
        if (locationLogging) {
            flipLogging();
        }
        if (locationStarted) {
            flipLocation();
        }
    }//onPause

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            //check all permissions
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                //have both permissions
                startLogging.setEnabled(true);
                startLogging.setBackground(ContextCompat.getDrawable(this, R.drawable.button_ready));
                startLocation.setEnabled(true);
                startLocation.setBackground(ContextCompat.getDrawable(this, R.drawable.button_ready));
            }
        }
    }//onRequestPermissionResult

    public void locationLogging(View view) {
        flipLogging();
    }

    private void flipLogging() {
        if (locationLogging) {
            //stop the task
            randomTask.cancel(true);
            startLogging.setText("START LOGGING");
            startLogging.setBackground(ContextCompat.getDrawable(this, R.drawable.button_ready));
            locationLogging = false;
        } else {
            //start the task
            if (!locationStarted) {
                flipLocation();
            }
            locationNameString = locationName.getText().toString();
            UPDATE_INTERVAL = (long) Long.parseLong(spinner_update.getSelectedItem().toString());
            FASTEST_INTERVAL = (long) Long.parseLong(spinner_fastest.getSelectedItem().toString());
            randomTask = exec.scheduleAtFixedRate(periodicTask,0,scanInterval, TimeUnit.MILLISECONDS);
            startLogging.setText("STOP LOGGING");
            startLogging.setBackground(ContextCompat.getDrawable(this, R.drawable.button_active));
        }
    }//flipLogging

    public void locationButtonClick(View view) { flipLocation(); }

    private void flipLocation() {
        if (locationStarted) {
            if (locationLogging) {
                flipLogging();
            }
            stopLocationUpdates();
        } else {
            startLocationUpdates();
        }
    }

    // Trigger new location updates at interval
    @SuppressLint("MissingPermission")
    protected void startLocationUpdates() {

        // Create the location request to start receiving updates
        mLocationRequest = new LocationRequest();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setInterval(UPDATE_INTERVAL);
        mLocationRequest.setFastestInterval(FASTEST_INTERVAL);

        // Create LocationSettingsRequest object using location request
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(mLocationRequest);
        LocationSettingsRequest locationSettingsRequest = builder.build();

        // Check whether location settings are satisfied
        SettingsClient settingsClient = LocationServices.getSettingsClient(this);
        settingsClient.checkLocationSettings(locationSettingsRequest);

        LocationServices.getFusedLocationProviderClient(this).requestLocationUpdates(mLocationRequest, locationCallback, Looper.myLooper());

        startLocation.setText("STOP LOCATION");
        startLocation.setBackground(ContextCompat.getDrawable(this, R.drawable.button_active));
        locationStarted = true;
    }//startLocationUpdates


    protected void stopLocationUpdates() {
        LocationServices.getFusedLocationProviderClient(this).removeLocationUpdates(locationCallback);
        startLocation.setText("START LOCATION");
        startLocation.setBackground(ContextCompat.getDrawable(this, R.drawable.button_ready));
        locationStarted = false;
    }

    public void onLocationChanged(Location location) {
        // New location has now been determined
        // You can now create a LatLng Object for use with maps
        latLng = new LatLng(location.getLatitude(), location.getLongitude());
        if (lastLatLng == null) { lastLatLng = latLng; }
    }

    @SuppressLint("MissingPermission")
    public void getLastLocation() {
        // Get last known recent location using new Google Play Services SDK (v11+)
        FusedLocationProviderClient locationClient = LocationServices.getFusedLocationProviderClient(this);

        locationClient.getLastLocation().addOnSuccessListener(new OnSuccessListener<Location>() {
            @Override
            public void onSuccess(Location location) {
                // GPS location can be null if GPS is switched off
                if (location != null) {
                    onLocationChanged(location);
                }
            }
        })
        .addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.d("MapDemoActivity", "Error trying to get last GPS location");
                e.printStackTrace();
            }
        });
    }//getLastLocation

    private double distance(double lat1, double lon1, double lat2, double lon2) {
        double theta = lon1 - lon2;
        double dist = Math.sin(deg2rad(lat1))
                * Math.sin(deg2rad(lat2))
                + Math.cos(deg2rad(lat1))
                * Math.cos(deg2rad(lat2))
                * Math.cos(deg2rad(theta));
        dist = Math.acos(dist);
        dist = rad2deg(dist);
        dist = dist * 60 * 1.1515;
        return (dist);
    }

    private double deg2rad(double deg) {
        return (deg * Math.PI / 180.0);
    }

    private double rad2deg(double rad) {
        return (rad * 180.0 / Math.PI);
    }

    private void writeToFile(String data) {
        String fileName = "location_logging.txt";
        File file;
        File saveFilePath;

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
            saveFilePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        } else  {
            saveFilePath = this.getExternalFilesDir(null);
        }
        file = new File(saveFilePath, fileName);

        data = data + "\n";

        try {
            FileOutputStream stream = new FileOutputStream(file, true);
            try {
                stream.write(data.getBytes());
            } finally {
                stream.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }//writeToFile

}