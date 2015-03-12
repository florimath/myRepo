package com.example.rolf.sunshine;

import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;

import org.achartengine.GraphicalView;
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
import java.util.Arrays;
import java.util.List;

public class ForecastFragment extends Fragment {

    public ForecastFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstancesState) {
        super.onCreate(savedInstancesState);
        this.setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        inflater.inflate(R.menu.forecastfragment, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        if (id == R.id.action_refresh) {
            FetchWeatherTask actualWeatherTask = new FetchWeatherTask();
            actualWeatherTask.execute("Berlin,de");
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    String[] forecastArray = {
            "   Press menu Refresh for retrieving weather data",
            " ", " ", " ", " ", " ", " "
    };

    int[] tempArray = {-3,-2,3,4,3,2,1};  // to be filled with actual temperatures later - for BarGraph


    //ArrayList<DayWeather> dayWeatherList = new ArrayList<DayWeather>();

    ArrayAdapter<String> myForecastAdapter;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        List<String> weekForecast = new ArrayList<String>( Arrays.asList(forecastArray) );

        myForecastAdapter = new ArrayAdapter<String> (
                getActivity(),
                R.layout.list_item_forecast,
                R.id.list_item_forecast_textview,
                weekForecast);

        ListView listView = (ListView) rootView.findViewById(R.id.listview_forecast);
        listView.setAdapter(myForecastAdapter);

        BarGraph barGraph = new BarGraph(tempArray);
        GraphicalView graphicalView = barGraph.getView(getActivity().getApplicationContext());
        LinearLayout layout = (LinearLayout) rootView.findViewById(R.id.bar_graph);
        layout.addView(graphicalView);
        graphicalView.setBackgroundColor(Color.RED);
        graphicalView.repaint();

        return rootView;
    }


    public class FetchWeatherTask extends AsyncTask<String, Void, String[]> {

        private final String LOG_TAG = FetchWeatherTask.class.getSimpleName();

        @Override
        protected String[] doInBackground(String... params) {

            // These two need to be declared outside the try/catch
            // so that they can be closed in the finally block.
            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;

            // Will contain the raw JSON response as a string.
            String forecastJsonStr = null;
            String format = "jason";
            String units = "metric";
            int numDays = 7;

            try {
                // Construct the URL for the OpenWeatherMap query
                // Possible parameters are avaiable at OWM's forecast API page, at
                // http://openweathermap.org/API#forecast
                /// rolf URL deprecated!???
                final String FORECAST_BASE_URL = "http://api.openweathermap.org/data/2.5/forecast/daily?";
                final String QUERY_PARAM = "q";
                final String FORMAT_PARAM = "mode";
                final String UNIT_PARAM = "units";
                final String DAYS_PARAM = "cnt";

                Uri builtUri = Uri.parse(FORECAST_BASE_URL).buildUpon()
                        .appendQueryParameter(QUERY_PARAM, params[0])
                                // set in zero position of params array, where there is the location
                        .appendQueryParameter(FORMAT_PARAM, format)
                        .appendQueryParameter(UNIT_PARAM, units)
                        .appendQueryParameter(DAYS_PARAM, Integer.toString(numDays))
                        .build();

                URL url = new URL(builtUri.toString());
                Log.v(LOG_TAG, "--> Built URI: " + url);

                // Create the request to OpenWeatherMap, and open the connection
                urlConnection = (HttpURLConnection) url.openConnection();
                // rolf why a type cast here??
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
                while ( (line = reader.readLine()) != null ) {
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
                Log.v(LOG_TAG, "--> forecastJsonStr: " + forecastJsonStr);

                // Pass string data to JSON object
                JSONObject forecastJsonObject = new JSONObject(forecastJsonStr);

                // Build array; "list" is the keyword in forecastJsonStr where the array follows
                JSONArray allDaysJsonArray = forecastJsonObject.getJSONArray("list");

                Log.v(LOG_TAG,"--> allDaysJsonArray length = " + allDaysJsonArray.length() );

                Time dayTime = new Time();
                dayTime.setToNow();  // ?? what happens here and why, later a new object is created
                int julianStartDay = Time.getJulianDay(System.currentTimeMillis(), dayTime.gmtoff); // ??

                dayTime = new Time();

                for(int i = 0; i < allDaysJsonArray.length(); i++) {
                    JSONObject jsonDayWeather = allDaysJsonArray.getJSONObject(i);
                    //DayWeather dayWeather = new DayWeather();  // DTO - data transfer object

                    JSONObject jsonTemperatureObject = jsonDayWeather.getJSONObject("temp");
                    String tempDay = jsonTemperatureObject.getString("day");
                    Double tempDayDouble = Double.parseDouble(tempDay);
                    long tempDayRounded = Math.round(tempDayDouble);

                    // the weather description is hidden in another array with keyword "weather"
                    // this array has only one JSONObject-field
                    JSONArray jsonDescriptionArray = jsonDayWeather.getJSONArray("weather");
                    String description = jsonDescriptionArray.getJSONObject(0).getString("description");

                    long dateTime = dayTime.setJulianDay(julianStartDay+i);
                    SimpleDateFormat shortenedDateFormat = new SimpleDateFormat("EEE MMM dd");
                    String day = shortenedDateFormat.format(dateTime);
                    if ( i==0 ) day = "Heute,  " + day;
                    if ( i==1 ) day = "Morgen, " + day;

                    //dayWeather.temp = tempDay;
                    //dayWeather.description = description;
                    Log.v(LOG_TAG,"--> day " + i + ":  "  + description + ", " + tempDayRounded + "°C, ");

                    //dayWeatherList.add(dayWeather);
                    forecastArray[i] = " " + day + ":  " + description + ", " + tempDayRounded + "°C";

                }

                return forecastArray;

            } catch (IOException e) {
                Log.e(LOG_TAG, "--> IO exception ", e);
                // If the code didn't successfully get the weather data,
                // there's no point in attempting to parse it.
                return null;

            } catch (JSONException e) {
                Log.e(LOG_TAG, "--> JSON exception ", e);

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

            return null;
        }

        @Override
        protected void onPostExecute(String[] result) {
        // the argument is automatically what the doInBackground method of AsynchTask returns
            if (result != null) {
                myForecastAdapter.clear();
                for (String day : result) {
                    // this is, what ultimately triggers the ListView zu update
                    myForecastAdapter.add(day);
                }
            }
            super.onPostExecute(result);
        }
    }
}

