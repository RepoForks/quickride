package com.sspm.quickride.ui.activity;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.CardView;
import android.text.InputType;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceAutocomplete;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.sspm.quickride.R;
import com.sspm.quickride.firebase_database.AbstractDatabaseReference;
import com.sspm.quickride.firebase_database.GeoFireReference;
import com.sspm.quickride.firebase_database.RidesReference;
import com.sspm.quickride.firebase_database.UserReference;
import com.sspm.quickride.pojo.Ride;
import com.sspm.quickride.pojo.User;
import com.sspm.quickride.ui.dialog.MessageDialog;
import com.sspm.quickride.ui.interfaces.Callback;
import com.sspm.quickride.util.IntentHelper;
import com.sspm.quickride.util.RequestCodes;


public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, GoogleApiClient.OnConnectionFailedListener, GoogleApiClient.ConnectionCallbacks {
    private GoogleMap mMap;
    private LocationManager mLocationManager;
    private int LOCATION_REFRESH_TIME = 1000;
    private float LOCATION_REFRESH_DISTANCE = 1;
    private Criteria mCriteria = new Criteria();
    private Location mMyLocation;
    private Marker mMarker;
    LocationRequest locationRequest;

    private GoogleApiClient mGoogleApiClient;
    TextView tv_source, tv_destination;
    FloatingActionButton fab;
    CardView linear;
    int topPadding, bottomPadding;
    Place pl_source = null, pl_destination = null;

    User user;
    UserReference userReference;
    RidesReference ridesReference;
    GeoFireReference geoFireReference;

    private final LocationListener mLocationListener = new LocationListener() {

        @Override
        public void onLocationChanged(final Location location) {
            //triggered when location changed.
            mMyLocation = location; //set mMyLocation to new location
            userReference.changeLocation(mMyLocation); //update it to database
            if (mMarker == null) //if first time
                setmMap();
            mMarker.setPosition(new LatLng(location.getLatitude(), location.getLongitude())); //update mMarker on mMap
        }

        @Override
        public void onStatusChanged(String s, int i, Bundle bundle) {
        }

        @Override
        public void onProviderEnabled(String s) {
            //triggered when GPS/Location Enabled.
            setmMap();
            Toast.makeText(getApplicationContext(), s, Toast.LENGTH_LONG).show();
        }

        @Override
        public void onProviderDisabled(String s) {
            //triggered when GPS/Location Disabled.
            Toast.makeText(getApplicationContext(), s, Toast.LENGTH_LONG).show();
            onStop();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //------------------------------------------------------------------------------------------
        //              Create an instance of GoogleAPIClient.
        //------------------------------------------------------------------------------------------
        connectGoogleApiClient();
        createLocationRequest();
        //------------------------------------------------------------------------------------------
        //------------------------------------------------------------------------------------------
        //              Initialise view
        //------------------------------------------------------------------------------------------
        setContentView(R.layout.activity_maps);

        getIntentData();

        init();
        //------------------------------------------------------------------------------------------
        //              Obtain the SupportMapFragment and get notified when the map is ready to be used
        //------------------------------------------------------------------------------------------
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        //------------------------------------------------------------------------------------------
        //              initialise Location Manager
        //------------------------------------------------------------------------------------------
        mLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

    }

    @Override
    protected void onResume() {
        connectGoogleApiClient();
        createLocationRequest();
        super.onResume();
    }

    private void connectGoogleApiClient(){
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
//              start instance of GoogleAPIClient.
            mGoogleApiClient.connect();
        }
    }

    private void getIntentData(){
        user = (User) getIntent().getSerializableExtra("user");
        AbstractDatabaseReference.setMyMobile(user.getMyMobile());
        initiateDatabase();
    }

    private void initiateDatabase(){
        userReference = new UserReference();
        userReference.initiateDatabase();

        ridesReference = new RidesReference();
        ridesReference.initiateDatabase();

        geoFireReference = new GeoFireReference();
        geoFireReference.initiateDatabase();
    }

    private void init() {

        tv_source = (TextView) findViewById(R.id.tv_source);
        tv_source.setInputType(InputType.TYPE_NULL);
        tv_source.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSearchView(RequestCodes.PLACE_AUTOCOMPLETE_REQUEST_CODE_SOURCE);
            }
        });

        tv_destination = (TextView) findViewById(R.id.tv_destination);
        tv_destination.setInputType(InputType.TYPE_NULL);
        tv_destination.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSearchView(RequestCodes.PLACE_AUTOCOMPLETE_REQUEST_CODE_DESTINATION);
            }
        });
        fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Ride ride = new Ride(AbstractDatabaseReference.getMyMobile(), String.valueOf(pl_source.getLatLng().latitude), String.valueOf(pl_source.getLatLng().longitude),String.valueOf(pl_destination.getLatLng().latitude), String.valueOf(pl_destination.getLatLng().longitude));
                ridesReference.addMyRide(ride);

            }
        });
        linear = (CardView) findViewById(R.id.linear);
        topPadding = getViewHeight(linear);

    }

    /**
     * Get the view height before the view will render
     *
     * @param view the view to measure
     * @return the height of the view
     */
    public static int getViewHeight(View view) {
        WindowManager wm =
                (WindowManager) view.getContext().getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();

        int deviceWidth;

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
            Point size = new Point();
            display.getSize(size);
            deviceWidth = size.x;
        } else {
            deviceWidth = display.getWidth();
        }

        int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(deviceWidth, View.MeasureSpec.AT_MOST);
        int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        view.measure(widthMeasureSpec, heightMeasureSpec);
        return view.getMeasuredHeight();
    }

    private void openSearchView(int requestCode) {
        try {
            Intent intent =
                    new PlaceAutocomplete.IntentBuilder(PlaceAutocomplete.MODE_FULLSCREEN)
                            .build(this);
            startActivityForResult(intent, requestCode);
        } catch (GooglePlayServicesRepairableException e) {
            // TODO: Handle the error.
        } catch (GooglePlayServicesNotAvailableException e) {
            // TODO: Handle the error.
        }
/*        below code is used to open google searchview directly in afragment instead of new activity        */

//        PlaceAutocompleteFragment autocompleteFragment = (PlaceAutocompleteFragment) getFragmentManager().findFragmentById(R.id.place_autocomplete_fragment);
//        ((EditText)autocompleteFragment).setHint("");
//        autocompleteFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
//            @Override
//            public void onPlaceSelected(Place place) {
//                Log.i("PLACE", "Place: " + place.getName());
//            }
//
//            @Override
//            public void onError(Status status) {
//                Log.i("PLACE", "An error occurred: " + status);
//            }
//        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RequestCodes.PLACE_AUTOCOMPLETE_REQUEST_CODE_SOURCE) {
            if (resultCode == RESULT_OK) {
                pl_source = PlaceAutocomplete.getPlace(this, data);
                tv_source.setText(pl_source.getName());
                Log.i("PLACE", "Place: " + pl_source.getName());
            } else if (resultCode == PlaceAutocomplete.RESULT_ERROR) {
                Status status = PlaceAutocomplete.getStatus(this, data);
                // TODO: Handle the error.
                Log.i("PLACE", status.getStatusMessage());

            } else if (resultCode == RESULT_CANCELED) {
                // The user canceled the operation.
            }
        } else if (requestCode == RequestCodes.PLACE_AUTOCOMPLETE_REQUEST_CODE_DESTINATION) {
            if (resultCode == RESULT_OK) {
                pl_destination = PlaceAutocomplete.getPlace(this, data);
                tv_destination.setText(pl_destination.getName());
                Log.i("PLACE", "Place: " + pl_destination.getName());
            } else if (resultCode == PlaceAutocomplete.RESULT_ERROR) {
                Status status = PlaceAutocomplete.getStatus(this, data);
                // TODO: Handle the error.
                Log.i("PLACE", status.getStatusMessage());

            } else if (resultCode == RESULT_CANCELED) {
                // The user canceled the operation.
            }
        }else if (requestCode == RequestCodes.REQUEST_CHECK_SETTINGS) {
            switch (resultCode) {
                case Activity.RESULT_OK:
                    // All required changes were successfully made
                    if (mGoogleApiClient.isConnected() /*&& userMarker == null*/) {
//                        startLocationUpdates
//                        setmMap();
                    }
                    break;
                case Activity.RESULT_CANCELED:
                    // The user was asked to change settings, but chose not to
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // An unresolvable error has occurred and a connection to Google APIs
        // could not be established. Display an error message, or handle
        // the failure silently (mostly no or failed internet connection)
        Toast.makeText(getApplicationContext(), "Google Services Error", Toast.LENGTH_LONG).show();
        onStop();
    }

    public void setmMap() {

        if(!isLocationPermissionAllowed()){
            checkLocationPermission();
            return;
        }

        mMap.setMyLocationEnabled(true);
        mMap.setPadding(0, topPadding, 0, bottomPadding);
        userReference.becameActive();
        userReference.changeLocation(mMyLocation);
        geoFireReference.setLocation(mMyLocation);
        if (mMyLocation != null) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(mMyLocation.getLatitude(), mMyLocation.getLongitude()), 13));
            mMarker = mMap.addMarker(new MarkerOptions().position(new LatLng(mMyLocation.getLatitude(), mMyLocation.getLongitude())));
            CameraPosition cameraPosition = new CameraPosition.Builder()
                    .target(new LatLng(mMyLocation.getLatitude(), mMyLocation.getLongitude()))      // Sets the center of the map to location user
                    .zoom(16)                   // Sets the zoom
                    .tilt(40)                   // Sets the tilt of the camera to 30 degrees
                    .build();                   // Creates a CameraPosition from the builder
            mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
        } else {
            Toast.makeText(getApplicationContext(), "Location Error", Toast.LENGTH_LONG).show();
            if (!mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                buildAlertMessageNoGps();
            }
        }
    }

    private void buildAlertMessageNoGps() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Your GPS seems to be disabled, do you want to enable it?")
                .setCancelable(false)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(@SuppressWarnings("unused") final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                        startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    public void onClick(final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
                        dialog.cancel();
                    }
                });
        final AlertDialog alert = builder.create();
        alert.show();
    }

    protected void onStop() {
        mGoogleApiClient.disconnect();
        if(mMarker != null)
            mMarker.remove();

        userReference.becameInActive();
        if(mMyLocation != null)
            geoFireReference.setLocation(mMyLocation);

        if(!isLocationPermissionAllowed()){
            checkLocationPermission();
            return;
        }
        mLocationManager.removeUpdates(mLocationListener);
        super.onStop();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
    } //map fragment is loaded asynchronously, when ready assign in to mMap

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        //request code is explicitly specified in this class (for location permission REQUEST CODE is set to 100). it can be set to any number.
        //The array permission[] consist of requested, and grantResult[] consist of result of it accordingly
        switch (requestCode) {
            case RequestCodes.REQUEST_CODE_LOCATION: {
                // If request is cancelled, the result arrays are empty. therefore (check array size AND check result is granted)
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //If condition = true permission was granted and therefore START using Location services
                    //noinspection MissingPermission
                    mMyLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
                    setmMap();

                } else {
                    // permission denied, boo! Prepare an activity to notify.
                }
                break;
            }
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        //mGoogleAPI was connected successfully.(APP START OR RESUME)
        //first check whether we have permission to use Location services.
        if(!isLocationPermissionAllowed()){
            checkLocationPermission();
            return;
        }

        if (!isGPSEnabled())
            promptForGpsPermission();
        else {

            //else Start using location services
            createLocationRequest();
            mMyLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
            if(mMyLocation != null)
                setmMap();
            //Toast.makeText(getApplicationContext(), "just connected!", Toast.LENGTH_LONG).show();
            mLocationManager.requestLocationUpdates(LOCATION_REFRESH_TIME, LOCATION_REFRESH_DISTANCE, mCriteria, mLocationListener, null);
        }
    }

    private void checkLocationPermission(){
            //if NOT then Request for Permission.
            ActivityCompat.requestPermissions(MapsActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, RequestCodes.REQUEST_CODE_LOCATION);
    }

    private boolean isLocationPermissionAllowed(){
        //first check whether we have permission to use Location services.
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onConnectionSuspended(int i) {
        if(mMarker != null)
            mMarker.remove();
        Toast.makeText(getApplicationContext(), "Connection Suspended", Toast.LENGTH_LONG).show();
        onStop();
    }

    public boolean isGPSEnabled() {
        LocationManager lm = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        boolean gpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        return gpsEnabled;
    }

    public void promptForGpsPermission() {
        MessageDialog dialog = new MessageDialog(this);
        dialog.setContent("Please Activate your GPS for proper navigation.");
        dialog.show(new Callback() {
            @Override
            public void onSuccess() {
                gotToGPSSetting();
            }

            @Override
            public void onFailure() {

            }
        });
    }

    public void createLocationRequest() {
        locationRequest = new LocationRequest();
        locationRequest.setInterval(1000);
        locationRequest.setFastestInterval(100);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setSmallestDisplacement(10.10f);
    }

    public void gotToGPSSetting() {
        IntentHelper.openGpsIntent();
    }

}