package me.shkschneider.openlocationcodes.demo;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.PolygonOptions;

import me.shkschneider.openlocationcodes.OpenLocationCodes;

public class MainActivity extends FragmentActivity {

    private GoogleMap mGoogleMap;
    private TextView mTextView;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle(getResources().getString(R.string.title));
        toolbar.setSubtitle(getResources().getString(R.string.subtitle));
        toolbar.inflateMenu(R.menu.main);
        toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(final MenuItem menuItem) {
                switch (menuItem.getItemId()) {
                    case R.id.menu_openlocationcode:
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://openlocationcode.com")));
                        return true;
                    case R.id.menu_sourcecode:
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/shkschneider/android_OpenLocationCode")));
                        return true;
                }
                return false;
            }
        });

        final FragmentManager fragmentManager = getSupportFragmentManager();
        final SupportMapFragment mapFragment = (SupportMapFragment) fragmentManager.findFragmentById(R.id.mapFragment);
        mapFragment.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(final GoogleMap googleMap) {
                mGoogleMap = googleMap;
                mGoogleMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
                mGoogleMap.getUiSettings().setZoomGesturesEnabled(false);
                mGoogleMap.getUiSettings().setZoomControlsEnabled(true);
                mGoogleMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {

                    @Override
                    public void onMapClick(final LatLng latLng) {
                        openLocationCode(latLng.latitude, latLng.longitude);
                    }

                });
                mGoogleMap.setMyLocationEnabled(true);
                mGoogleMap.getUiSettings().setMyLocationButtonEnabled(true);
                mGoogleMap.setOnMyLocationButtonClickListener(new GoogleMap.OnMyLocationButtonClickListener() {
                    @Override
                    public boolean onMyLocationButtonClick() {
                        final Location location = mGoogleMap.getMyLocation();
                        if (location != null) {
                            openLocationCode(location.getLatitude(), location.getLongitude());
                            return true;
                        }
                        return false;
                    }
                });
                mGoogleMap.setOnMyLocationChangeListener(new GoogleMap.OnMyLocationChangeListener() {
                    @Override
                    public void onMyLocationChange(final Location location) {
                        openLocationCode(location.getLatitude(), location.getLongitude());
                        mGoogleMap.setOnMyLocationChangeListener(null);
                    }
                });
            }
        });

        mTextView = (TextView) findViewById(R.id.textView);

        final SharedPreferences sharedPreferences = getSharedPreferences("openlocationcodes", MODE_PRIVATE);
        if (! sharedPreferences.getBoolean("credits", false)) {
            new AlertDialog.Builder(MainActivity.this)
                    .setMessage(getResources().getString(R.string.credits))
                    .setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(final DialogInterface dialogInterface, final int which) {
                            dialogInterface.dismiss();
                            sharedPreferences.edit().putBoolean("credits", true).apply();
                        }
                    })
                    .show();
        }
    }

    private void openLocationCode(final double latitude, final double longitude) {
        final String openLocationCode = OpenLocationCodes.encode(latitude, longitude);
        mTextView.setText(openLocationCode);
        mTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://plus.codes/" + openLocationCode)));
            }
        });

        final OpenLocationCodes.CodeArea codeArea = OpenLocationCodes.decode(openLocationCode);
        mGoogleMap.clear();
        mGoogleMap.addPolygon(new PolygonOptions()
                .add(codeArea.northwest())
                .add(codeArea.southwest())
                .add(codeArea.southeast())
                .add(codeArea.northeast())
                .strokeColor(getResources().getColor(R.color.accentColor))
                .strokeWidth(8.0F)
                .fillColor(Color.TRANSPARENT));
        mGoogleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(codeArea.center(), 19));
    }

}
