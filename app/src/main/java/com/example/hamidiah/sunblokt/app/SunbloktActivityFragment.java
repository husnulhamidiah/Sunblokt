package com.example.hamidiah.sunblokt.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * A placeholder fragment containing a simple view.
 */
public class SunbloktActivityFragment extends Fragment {

    private ArrayAdapter<String> mForecastAdapter;

    public SunbloktActivityFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_sunblokt, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            startActivity(new Intent(getActivity(), PreferenceActivity.class));
            return true;
        } else if (id == R.id.action_refresh) {
            updateWeather();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onStart() {
        super.onStart();
        updateWeather();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_sunblokt, container, false);

        List<String> weekForecast = new ArrayList<String>();

        mForecastAdapter = new ArrayAdapter<>(getActivity(), R.layout.list_item_forecast, R.id.list_item_forecast_textview, weekForecast);

        ListView listView = (ListView) rootView.findViewById(R.id.listview_forecast);
        listView.setAdapter(mForecastAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String forecast = mForecastAdapter.getItem(position);
                Toast.makeText(getActivity(), forecast, Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(getActivity(), DetailActivity.class)
                        .putExtra(Intent.EXTRA_TEXT, forecast);
                startActivity(intent);
            }
        });

        return rootView;
    }

    private void updateWeather() {
        FetchWeatherTask weatherTask = new FetchWeatherTask();
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getActivity());
        String city = pref.getString(getString(R.string.pref_location_key),
                getString(R.string.pref_location_default));
        weatherTask.execute(city);
    }

    public class FetchWeatherTask extends AsyncTask<String, String, List<String>> {

        public class ForecastDetails {

            String name;
            Double lon, lat;
            List<String> forecasts;

            public ForecastDetails(String name, Double lon, Double lat) {
                this.name = name;
                this.lon = lon;
                this.lat = lat;
                this.forecasts = new ArrayList<>();
            }

            public void addForecast(String forecast) {
                this.forecasts.add(forecast);
            }
        }

        private final String LOG_TAG = FetchWeatherTask.class.getSimpleName();

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
        private String formatHighLows(double high, double low) {

            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences((getActivity()));
            String unitType = pref.getString(
                    getString(R.string.pref_temperature_unit_key),
                    getString(R.string.pref_temperature_unit_metric)
            );

            if (unitType.equals(getString(R.string.pref_temperature_unit_imperial))) {
                high = (high * 1.8) + 32;
                low = (low * 1.8) + 32;
            } else if (!unitType.equals(getString(R.string.pref_temperature_unit_metric))) {
                Log.d(LOG_TAG, "Unit type not found :" + unitType);
            }

            // For presentation, assume the user doesn't care about tenths of a degree.
            long roundedHigh = Math.round(high);
            long roundedLow = Math.round(low);

            String highLowStr = roundedHigh + "/" + roundedLow;
            return highLowStr;
        }

        /**
         * Take the String representing the complete forecast in JSON Format and
         * pull out the data we need to construct the Strings needed for the wireframes.
         *
         * Fortunately parsing is easy:  constructor takes the JSON string and converts it
         * into an Object hierarchy for us.
         */
        private ForecastDetails getWeatherDataFromJson(String forecastJsonStr, int numDays)
                throws JSONException {

            ForecastDetails forecastResult = null;

            final String OWM_CITY = "city";
            final String OWM_NAME = "name";
            final String OWM_COORD = "coord";
            final String OWM_LON = "lon";
            final String OWM_LAT = "lat";
            final String OWM_LIST = "list";
            final String OWM_WEATHER = "weather";
            final String OWM_TEMPERATURE = "temp";
            final String OWM_MAX = "max";
            final String OWM_MIN = "min";
            final String OWM_DESCRIPTION = "main";

            JSONObject forecastJson = new JSONObject(forecastJsonStr);

            JSONObject cityJson = forecastJson.getJSONObject(OWM_CITY);
            String name = cityJson.getString(OWM_NAME);

            JSONObject coordJson = cityJson.getJSONObject(OWM_COORD);
            Double lon = coordJson.getDouble(OWM_LON);
            Double lat = coordJson.getDouble(OWM_LAT);

            forecastResult = new ForecastDetails(name, lon, lat);

            JSONArray weatherArray = forecastJson.getJSONArray(OWM_LIST);

            // OWM returns daily forecasts based upon the local time of the city that is being
            // asked for, which means that we need to know the GMT offset to translate this data
            // properly.

            // Since this data is also sent in-order and the first day is always the
            // current day, we're going to take advantage of that to get a nice
            // normalized UTC date for all of our weather.

            // TODO : Use Georgian Calendar instead of Time which is deprecated since API level 22.
            Time dayTime = new Time();
            dayTime.setToNow();

            // we start at the day returned by local time. Otherwise this is a mess.
            int julianStartDay = Time.getJulianDay(System.currentTimeMillis(), dayTime.gmtoff);

            // now we work exclusively in UTC
            dayTime = new Time();

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

                highAndLow = formatHighLows(high, low);

                String forecastStr = day + " - " + description + " - " + highAndLow;
                forecastResult.addForecast(forecastStr);
            }

            for (String s : forecastResult.forecasts) {
                Log.v(LOG_TAG, "Forecast entry : " + s);
            }

            return forecastResult;
        }

        @Override
        protected List<String> doInBackground(String... params) {

            if (params.length == 0) {
                return null;
            }

            // These two need to be declared outside the try/catch so that they can be
            // closed in the finally block.
            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;

            // Will contain the raw JSON response as a string.
            String forecastJsonStr = null;

            String city = params[0];
            String format = "json";
            String units = "metric";
            String appid = "92f6755537b42a23e8128d599de128f1";
            int numDays = 7;

            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getActivity());
            Boolean isAdvance = pref.getBoolean(getString(R.string.pref_advance_key), false);

            String lat = pref.getString(getString(R.string.pref_lat_key),
                    getString(R.string.pref_lat_default));

            String lng = pref.getString(getString(R.string.pref_lon_key),
                    getString(R.string.pref_lon_default));

            try {
                // Construct the URL for the OpenWeatherMap query
                // Possible parameters are avaiable at OWM's forecast API page, at http://openweathermap.org/API#forecast
                final String FORECAST_BASE_URL = "http://api.openweathermap.org/data/2.5/forecast/daily?";
                final String QUERY_PARAM = "q";
                final String QUERY_LAT = "lat";
                final String QUERY_LNG = "lon";
                final String FORMAT_PARAM = "mode";
                final String UNITS_PARAM = "units";
                final String DAYS_PARAM = "cnt";
                final String APP_KEY = "appid";

                Uri.Builder builtUri = Uri.parse(FORECAST_BASE_URL).buildUpon()
                        .appendQueryParameter(FORMAT_PARAM, format)
                        .appendQueryParameter(UNITS_PARAM, units)
                        .appendQueryParameter(DAYS_PARAM, Integer.toString(numDays))
                        .appendQueryParameter(APP_KEY, appid);

                if (isAdvance) {
                    builtUri.appendQueryParameter(QUERY_LAT, lat);
                    builtUri.appendQueryParameter(QUERY_LNG, lng);
                } else {
                    builtUri.appendQueryParameter(QUERY_PARAM, city);
                }

                URL url = new URL(builtUri.build().toString());
                Log.v(LOG_TAG, "Built URI : " + builtUri.toString());

                // Create the request to OpenWeatherMap, and open the connection
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();

                // Read the input stream into a String
                InputStream inputStream = urlConnection.getInputStream();
                StringBuffer buffer = new StringBuffer();
                if (inputStream == null) {
                    // Nothing to do.
                    return null;
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

                Log.v(LOG_TAG, "Forecast JSON String : " + forecastJsonStr);

            } catch (IOException e) {
                Log.e("PlaceholderFragment", "Error ", e);
                // If the code didn't successfully get the weather data, there's no point in attemping
                // to parse it.
                return null;
            } finally{
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (final IOException e) {
                        Log.e("PlaceholderFragment", "Error closing stream", e);
                    }
                }
            }

            try {
                ForecastDetails weatherData = getWeatherDataFromJson(forecastJsonStr, numDays);
                publishProgress(weatherData.name);
                return weatherData.forecasts;
            } catch (JSONException e) {
                Log.e("PlaceholderFragment", e.getMessage(), e);
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            super.onProgressUpdate(values);
            Toast.makeText(getActivity(), values[0], Toast.LENGTH_SHORT).show();
        }

        @Override
        protected void onPostExecute(List<String> result) {
            if (result != null) {
                mForecastAdapter.clear();
                for (String dayForecastStr : result) {
                    mForecastAdapter.add(dayForecastStr);
                }
            }
        }
    }
}
