package com.example.hamidiah.sunblokt.app;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A placeholder fragment containing a simple view.
 */
public class SunbloktActivityFragment extends Fragment {

    private ArrayAdapter<String> mForecastAdapter;

    public SunbloktActivityFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_sunblokt, container, false);

        // Create some dummy data
        String[] forecastArray = {
                "Today - Sunny - 88/54",
                "Tomorrow - Foggy - 70.40",
                "Weds - Cloudy - 72/63",
                "Thrus - Asteroids - 76/56",
                "Fri - Heavy Rain - 65/56",
                "Sat - IT CAN'T BE PREDICTED - ??/??",
                "Sun - Sunny - 78/65"
        };

        List<String> weekForecast = new ArrayList<>(
                Arrays.asList(forecastArray)
        );

        mForecastAdapter = new ArrayAdapter<>(getActivity(), R.layout.list_item_forecast, R.id.list_item_forecast_textview, weekForecast);

        ListView listView = (ListView) rootView.findViewById(R.id.listview_forecast);
        listView.setAdapter(mForecastAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String forecast = mForecastAdapter.getItem(position);
//                Toast.makeText(getActivity(), forecast, Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(getActivity(), DetailActivity.class)
                        .putExtra(Intent.EXTRA_TEXT, forecast);
                startActivity(intent);
            }
        });

        return rootView;
    }
}
