package me.shkschneider.openlocationcodes.demo;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.util.concurrent.TimeUnit;

public class Locator implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {

    private Context mContext;
    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;
    private LocationListener mLocationListener;
    private Location mLocation;

    public Locator(@NonNull final Context context) {
        mContext = context;
        mGoogleApiClient = new GoogleApiClient.Builder(mContext)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        mLocationRequest = defaultLocationRequest();
        mLocation = null;

        final LocationManager locationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            Log.i("OpenLocationCodes", "Network provider unavailable");
        }
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Log.i("OpenLocationCodes", "GPS provider unavailable");
        }
    }

    private LocationRequest defaultLocationRequest() {
        return LocationRequest.create()
                .setInterval(TimeUnit.MINUTES.toMillis(1))
                .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
    }

    public boolean start(@Nullable final LocationRequest locationRequest, @Nullable final LocationListener locationListener) {
        int providers = 0;
        final LocationManager locationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
        if (! locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            Log.i("OpenLocationCodes", "Network provider unavailable");
        }
        else {
            providers++;
        }
        if (! locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Log.i("OpenLocationCodes", "GPS provider unavailable");
        }
        else {
            providers++;
        }
        if (providers == 0) {
            return false;
        }

        if (mGoogleApiClient.isConnected()) {
            Log.i("OpenLocationCodes", "GoogleApiClient was already connected");
            if (mLocation != null) {
                mLocationListener.onLocationChanged(mLocation);
            }
            return false;
        }
        if (mGoogleApiClient.isConnecting()) {
            Log.i("OpenLocationCodes", "GoogleApiClient was connecting");
            if (mLocation != null) {
                mLocationListener.onLocationChanged(mLocation);
            }
            return false;
        }

        mGoogleApiClient.connect();
        if (locationRequest == null) {
            Log.v("OpenLocationCodes", "LocationRequest was NULL");
            mLocationRequest = defaultLocationRequest();
        }
        else {
            mLocationRequest = locationRequest;
        }
        mLocationListener = locationListener;
        return true;
    }

    public Location location() {
        return mLocation;
    }

    public boolean stop() {
        if (! mGoogleApiClient.isConnected()) {
            Log.d("OpenLocationCodes", "GoogleApiClient was disconnected");
            return true;
        }

        try {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
            mGoogleApiClient.unregisterConnectionCallbacks(this);
            mGoogleApiClient.unregisterConnectionFailedListener(this);
            mGoogleApiClient.disconnect();
            mLocationRequest = null;
            mLocationListener = null;
            return true;
        }
        catch (final Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // GoogleApiClient.ConnectionCallbacks

    @Override
    public void onConnected(final Bundle bundle) {
        if (! mGoogleApiClient.isConnected()) {
            Log.w("OpenLocationCodes", "GoogleApiClient was disconnected");
            return;
        }
        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED) {
            // TODO
            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this).setResultCallback(new ResultCallback<Status>() {
            @Override
            public void onResult(@NonNull final Status status) {
                if (status.isSuccess()) {
                    Log.d("OpenLocationCodes", "LocationServices: SUCCESS");
                    final LocationAvailability locationAvailability = LocationServices.FusedLocationApi.getLocationAvailability(mGoogleApiClient);
                    if (locationAvailability != null && locationAvailability.isLocationAvailable()) {
                        onLocationChanged(LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient));
                    }
                } else if (status.isCanceled()) {
                    Log.d("OpenLocationCodes", "LocationServices: CANCELED");
                } else if (status.isInterrupted()) {
                    Log.d("OpenLocationCodes", "LocationServices: INTERRUPTED");
                }
            }
        });
    }

    @Override
    public void onConnectionSuspended(final int reason) {
        switch (reason) {
            case CAUSE_NETWORK_LOST:
                Log.w("OpenLocationCodes", "GoogleApiClient: NETWORK_LOST");
                break ;
            case CAUSE_SERVICE_DISCONNECTED:
                Log.w("OpenLocationCodes", "GoogleApiClient: SERVICE_DISCONNECTED");
                break ;
        }
    }

    // GoogleApiClient.OnConnectionFailedListener

    @Override
    public void onConnectionFailed(@NonNull final ConnectionResult connectionResult) {
        Log.w("OpenLocationCodes", "GoogleApiClient:" + connectionResult.getErrorCode());
    }

    // LocationListener

    @Override
    public void onLocationChanged(final Location location) {
        if (location == null) {
            return;
        }
        mLocation = location;
        if (mLocationListener != null) {
            mLocationListener.onLocationChanged(mLocation);
        }
    }

}
