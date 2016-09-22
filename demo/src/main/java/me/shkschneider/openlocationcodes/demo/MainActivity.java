package me.shkschneider.openlocationcodes.demo;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.PolygonOptions;

import java.util.Locale;

import me.shkschneider.openlocationcodes.OpenLocationCodes;

public class MainActivity extends AppCompatActivity {

    private GoogleMap mGoogleMap;
    private TextView mTextView;
    private int mCodeLength = OpenLocationCodes.CODE_DEFAULT_LENGTH;

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
                        openLocationCode(latLng.latitude, latLng.longitude, mCodeLength, false);
                    }
                });
                mGoogleMap.setMyLocationEnabled(true);
                mGoogleMap.getUiSettings().setMyLocationButtonEnabled(true);
                final Locator locator = new Locator(MainActivity.this);
                locator.start(LocationRequest.create().setNumUpdates(1).setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY), new LocationListener() {
                    @Override
                    public void onLocationChanged(final Location location) {
                        update(location, true);
                    }
                });
                mGoogleMap.setOnMyLocationButtonClickListener(new GoogleMap.OnMyLocationButtonClickListener() {
                    @Override
                    public boolean onMyLocationButtonClick() {
                        final Location location = locator.location();
                        if (location == null) {
                            return false;
                        }
                        update(location, false);
                        return true;
                    }
                });
                mGoogleMap.setOnCameraIdleListener(new GoogleMap.OnCameraIdleListener() {
                    @Override
                    public void onCameraIdle() {
                        final Location location = locator.location();
                        if (location == null) {
                            return;
                        }
                        update(location, false);
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
                        public void onClick(final DialogInterface dialogInterface, final int i) {
                            dialogInterface.dismiss();
                            sharedPreferences.edit().putBoolean("credits", true).apply();
                        }
                    })
                    .show();
        }
    }

    private float mZoom = 2.0F;

    private void update(final Location location, final boolean animate) {
        if (mGoogleMap.getCameraPosition().zoom != mZoom) {
            mZoom = mGoogleMap.getCameraPosition().zoom;
            switch ((int) mZoom) {
                case 20:
                case 19:
                case 18:
                    mCodeLength = 10;
                    break;
                case 17:
                case 16:
                case 15:
                case 14:
                case 13:
                case 12:
                    mCodeLength = 8;
                    break;
                case 11:
                case 10:
                case 9:
                case 8:
                case 7:
                case 6:
                case 5:
                    mCodeLength = 4;
                    break;
                case 4:
                case 3:
                case 2:
                case 1:
                    mCodeLength = 2;
                    break;
                default:
                    mCodeLength = OpenLocationCodes.CODE_DEFAULT_LENGTH;
                    break;
            }
            openLocationCode(location.getLatitude(), location.getLongitude(), mCodeLength, animate);
            return;
        }
        openLocationCode(location.getLatitude(), location.getLongitude(), mCodeLength, animate);
    }

    private void openLocationCode(final double latitude, final double longitude, final int codeLength, final boolean animate) {
        final String openLocationCode = OpenLocationCodes.encode(latitude, longitude, codeLength);

        if (! OpenLocationCodes.isPadded(openLocationCode)) {
            final String shorten = OpenLocationCodes.shorten(openLocationCode, latitude, longitude);
            final String recovered = OpenLocationCodes.recover(shorten, latitude, longitude, codeLength);
        }

        final OpenLocationCodes.CodeArea codeArea = OpenLocationCodes.decode(openLocationCode);
        final float distanceInMeters = OpenLocationCodes.distance(codeArea);

        mTextView.setText(String.format(Locale.US, "%f / %f\n%s\n~%.2fm", latitude, longitude, openLocationCode, distanceInMeters));
        mTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://plus.codes/" + openLocationCode)));
            }
        });

        mGoogleMap.clear();
        mGoogleMap.addPolygon(new PolygonOptions()
                .add(codeArea.northwest())
                .add(codeArea.southwest())
                .add(codeArea.southeast())
                .add(codeArea.northeast())
                .strokeColor(getResources().getColor(R.color.accentColor))
                .strokeWidth(8.0F)
                .fillColor(Color.TRANSPARENT));
        if (animate) {
            mGoogleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(codeArea.bounds(), (int) getResources().getDimension(R.dimen.spaceMedium)));
        }
    }

}
