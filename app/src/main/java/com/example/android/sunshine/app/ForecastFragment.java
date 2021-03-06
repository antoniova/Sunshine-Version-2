package com.example.android.sunshine.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcel;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.BufferUnderflowException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;


/**
 * Created by theone on 1/15/16.
 */
public class ForecastFragment extends Fragment {

    static final String APPID = "f9fe58802ded38534b8a0ad6dd40b77b";
    static final int FORECAST_LENGTH = 7;
    private Uri mGeoLocation = null;

    private ArrayAdapter<String> mForecastAdapter;

    public ForecastFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        // We would like to add items the menu from this fragment
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        //List<String> data = new ArrayList<>();

        //for( int i = 0 ; i < 7; i++)
          //  data.add("Today - " + Integer.toString(i) + " - 88/63");

        mForecastAdapter = new ArrayAdapter<>(getActivity(),
                R.layout.list_item_forecast, R.id.list_item_forecast_textview,
                new ArrayList<String>() );
        ListView mListview = (ListView) rootView.findViewById(R.id.listview_forecast);
        mListview.setAdapter(mForecastAdapter);
        mListview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent intent = new Intent(getActivity(), DetailActivity.class);
                intent.putExtra(Intent.EXTRA_TEXT, mForecastAdapter.getItem(position));
                startActivity(intent);
            }
        });
        return rootView;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater menuInflater){
        // add items to the menu
        menuInflater.inflate(R.menu.forecastfragment, menu);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem){
        //todo: need to add logic to handle run-time permissions
        switch(menuItem.getItemId()){
            case R.id.action_refresh:
                updateWeather();
                return true;
            default:
                return super.onOptionsItemSelected(menuItem);
        }
    }

    @Override
    public void onStart(){
        super.onStart();
        updateWeather();
    }

    public void updateWeather(){
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getActivity());
        StringBuilder locationQuery = new StringBuilder();
        locationQuery.append(sharedPref.getString(getString(R.string.pref_location_key), ""))
                .append(",")
                .append(sharedPref.getString(getString(R.string.pref_country_key), ""));
        //String unitsFormat = sharedPref.getString(getString(R.string.pref_units_key), "");
        //Log.d("FORECAST_FRAGMENT", "query: " + locationQuery.toString());
        new FetchWeatherTask().execute(locationQuery.toString());
    }

    public class FetchWeatherTask extends AsyncTask<String, Void, List<String>>{

        private final String  LOG_TAG = FetchWeatherTask.class.getSimpleName();

        @Override
        protected List<String> doInBackground(String... params) {

            if( params.length == 0 ){
                return null;
            }
            // These two need to be declared outside the try/catch
            // so that they can be closed in the finally block.
            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;

            // Will contain the raw JSON response as a string.
            String forecastJsonStr = null;

            // query values to be used when building URL
            String format = "json";
            String units = getString(R.string.pref_units_default);
            //int numDays = 7;

            try {
                // Construct the URL for the OpenWeatherMap query
                // Possible parameters are available at OWM's forecast API page, at
                // http://openweathermap.org/API#forecast
                final String BASE_URL =
                        "http://api.openweathermap.org/data/2.5/forecast/daily?";
                final String QUERY_PARAM = "q";
                final String FORMAT_PARAM = "mode";
                final String UNITS_PARAM = "units";
                final String DAYS_PARAM = "cnt";
                final String APPID_PARAM = "appid";

                Uri builtUri = Uri.parse(BASE_URL).buildUpon()
                        .appendQueryParameter(QUERY_PARAM, params[0])
                        .appendQueryParameter(FORMAT_PARAM, format)
                        .appendQueryParameter(UNITS_PARAM, units)
                        .appendQueryParameter(DAYS_PARAM, Integer.toString(FORECAST_LENGTH))
                        .appendQueryParameter(APPID_PARAM, APPID)
                        .build();

                URL url = new URL(builtUri.toString());

                //Log.v(LOG_TAG, "Built URI: " + builtUri.toString());

                //URL url = new URL("http://api.openweathermap.org/data/2.5/forecast/daily?q=94043,us&units=metric&cnt=7&APPID=" + APPID );

                // Create the request to OpenWeatherMap, and open the connection
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();

                // Read the input stream into a String
                InputStream inputStream = urlConnection.getInputStream();
                StringBuffer buffer = new StringBuffer();
                if (inputStream == null) {
                    // Nothing to do.
                    return  null;
                }
                reader = new BufferedReader(new InputStreamReader(inputStream));

                String line;
                while ((line = reader.readLine()) != null) {
                    // Since it's JSON, adding a newline isn't necessary (it won't affect parsing)
                    // But it does make debugging a *lot* easier if you print out the completed
                    // buffer for debugging.
                    buffer.append(line + "\n");
                }

                if (buffer.length() == 0) {
                    // Stream was empty.  No point in parsing.
                    return null;
                }
                forecastJsonStr = buffer.toString();
                // Log.v(LOG_TAG, forecastJsonStr);
                return getWeatherDataFromJson(forecastJsonStr, FORECAST_LENGTH);

            } catch (IOException e) {
                Log.e(LOG_TAG, "Error ", e);
                // If the code didn't successfully get the weather data, there's no point in attempting
                // to parse it.
                return null;
            } catch (JSONException e){
                Log.e(LOG_TAG, "Error ", e);
                return null;
            } finally{
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (final IOException e) {
                        Log.e(LOG_TAG, "Error closing stream", e);
                    }
                }
            }
        }

        /* The date/time conversion code is going to be moved outside the asynctask later,
         * so for convenience we're breaking it out into its own method now.
         */
        private String getReadableDateString(long time){
            // Because the API returns a unix timestamp (measured in seconds),
            // it must be converted to milliseconds in order to be converted to valid date.
            SimpleDateFormat shortenedDateFormat = new SimpleDateFormat("EEE MMM dd");
            return shortenedDateFormat.format(time);
        }

        /**
         * Prepare the weather high/lows for presentation.
         */
        private String formatHighLows(double high, double low, String unitType) {
            // We check which unit format the user requested. Default format
            // is metric.
            if(!unitType.equals(getString(R.string.pref_units_default))){
                high = (high * 1.8) + 32;
                low = (low * 1.8) + 32;
            }

            // For presentation, assume the user doesn't care about tenths of a degree.
            long roundedHigh = Math.round(high);
            long roundedLow = Math.round(low);
            //String highLowStr = roundedHigh + "/" + roundedLow;
            //return highLowStr;
            return roundedHigh + "/" + roundedLow;
        }

        /**
         * Take the String representing the complete forecast in JSON Format and
         * pull out the data we need to construct the Strings needed for the wireframes.
         *
         * Fortunately parsing is easy:  constructor takes the JSON string and converts it
         * into an Object hierarchy for us.
         */
        private List<String> getWeatherDataFromJson(String forecastJsonStr, int numDays)
                throws JSONException {

            // These are the names of the JSON objects that need to be extracted.
            final String OWM_LIST = "list";
            final String OWM_WEATHER = "weather";
            final String OWM_TEMPERATURE = "temp";
            final String OWM_MAX = "max";
            final String OWM_MIN = "min";
            final String OWM_DESCRIPTION = "main";

            JSONObject forecastJson = new JSONObject(forecastJsonStr);
            JSONArray weatherArray = forecastJson.getJSONArray(OWM_LIST);

            // OWM returns daily forecasts based upon the local time of the city that is being
            // asked for, which means that we need to know the GMT offset to translate this data
            // properly.

            // Since this data is also sent in-order and the first day is always the
            // current day, we're going to take advantage of that to get a nice
            // normalized UTC date for all of our weather.

            Time dayTime = new Time();
            dayTime.setToNow();

            // we start at the day returned by local time. Otherwise this is a mess.
            int julianStartDay = Time.getJulianDay(System.currentTimeMillis(), dayTime.gmtoff);

            // now we work exclusively in UTC
            dayTime = new Time();

            SharedPreferences sharedPrefs =
                    PreferenceManager.getDefaultSharedPreferences(getActivity());
            String unitType = sharedPrefs.getString(
                    getString(R.string.pref_units_key),
                    getString(R.string.pref_units_default));

            List<String> resultStrs = new ArrayList<>();
            for(int i = 0; i < weatherArray.length(); i++) {
                // For now, using the format "Day, description, hi/low"
                String day;
                String description;
                String highAndLow;

                // Get the JSON object representing the day
                JSONObject dayForecast = weatherArray.getJSONObject(i);

                // The date/time is returned as a long.  We need to convert that
                // into something human-readable, since most people won't read "1400356800" as
                // "this saturday".
                long dateTime;
                // Cheating to convert this to UTC time, which is what we want anyhow
                dateTime = dayTime.setJulianDay(julianStartDay+i);
                day = getReadableDateString(dateTime);

                // description is in a child array called "weather", which is 1 element long.
                JSONObject weatherObject = dayForecast.getJSONArray(OWM_WEATHER).getJSONObject(0);
                description = weatherObject.getString(OWM_DESCRIPTION);

                // Temperatures are in a child object called "temp".  Try not to name variables
                // "temp" when working with temperature.  It confuses everybody.
                JSONObject temperatureObject = dayForecast.getJSONObject(OWM_TEMPERATURE);
                double high = temperatureObject.getDouble(OWM_MAX);
                double low = temperatureObject.getDouble(OWM_MIN);

                highAndLow = formatHighLows(high, low, unitType);
                //resultStrs[i] = day + " - " + description + " - " + highAndLow;
                resultStrs.add(day + " - " + description + " - " + highAndLow);
            }
            // let's extract the geolocation data and append it to the String array
            // to be returned
            String geoLocation = "geo:";
            geoLocation += forecastJson.getJSONObject("city")
                    .getJSONObject("coord").getString("lon");
            geoLocation += ",";
            geoLocation += forecastJson.getJSONObject("city")
                    .getJSONObject("coord").getString("lat");
            resultStrs.add(geoLocation);
            return resultStrs;
        }

        /**
         * Invoked after doInBackground() returns. This method runs in the UI thread
         * @param resultStrs The daily forecast data to be posted on the main UI thread
         */
        @Override
        protected void onPostExecute(List<String> resultStrs){
            // Let's populate the listview with real weather data.
            // Do not add the last element in the array to the listview as it
            // contains the geolocation data.
            int lastIndex = resultStrs.size() - 1 ;
            mGeoLocation = Uri.parse(resultStrs.get(lastIndex));
            resultStrs.remove(lastIndex);
            mForecastAdapter.clear();
            for(String str : resultStrs){
                mForecastAdapter.add(str);
            }
            mForecastAdapter.notifyDataSetChanged();

            Log.d(LOG_TAG, mGeoLocation.toString());
        }

    }

}
