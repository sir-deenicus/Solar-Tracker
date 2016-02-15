package Solar.Light;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.*;
import com.github.dvdme.ForecastIOLib.FIOCurrently;
import com.github.dvdme.ForecastIOLib.FIODataPoint;
import com.github.dvdme.ForecastIOLib.ForecastIO;

import java.io.*;
import java.util.ArrayList;
import java.util.Calendar;

public class SolarLightTracker extends Activity {
    /**
     * Called when the activity is first created.
     */

    TextView textLIGHT_reading, locDisp;
    Button saveButton;

    double longitude, latitude;
    private double ccover;
    boolean readingSensor = false;

    String saveInfo = "";

    LocationManager lm;
    String path = Environment.getExternalStorageDirectory() + "/Documents/";
    ArrayList<Double> lightSensorDataBuffer = new ArrayList<>();

    private String info;
    private TextView textViewInfo;
    private boolean hasSensed = false;

    Double [] mapToDouble(String [] arr){
        Double [] newArr = new Double[arr.length];
        for(int i=0; i < arr.length; i ++){
           newArr[i] = Double.parseDouble(arr[i]);
        }
        return newArr;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        textLIGHT_reading = (TextView)findViewById(R.id.LIGHT_reading);
        locDisp = (TextView) findViewById(R.id.textViewLOC);
        textViewInfo = (TextView)findViewById(R.id.textViewInfo);
        
        final ToggleButton gpsButton = (ToggleButton) findViewById(R.id.toggleButtonGPS);
        final Button getDataButton = (Button) findViewById(R.id.buttonLight);
        final Button weatherButton = (Button) findViewById(R.id.buttonCloud);
        final CheckBox chargingBox = (CheckBox) findViewById(R.id.checkBoxCharging);

        saveButton = (Button) findViewById(R.id.buttonSave);

        final ForecastIO fio = new ForecastIO(getResources().getString(R.string.apiKey));
        fio.setUnits(ForecastIO.UNITS_US);

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int id = ((RadioGroup) findViewById(R.id.radGroup)).getCheckedRadioButtonId();
                String outOrIn = ((RadioButton) findViewById(id)).getText().toString();
                String amps = ((EditText) findViewById(R.id.editTextAmpReading)).getText().toString();
                double ampAvgFloat = Average(mapToDouble(amps.split(",")));
                String ampAvg = String.valueOf(ampAvgFloat);
                double altAmp = ampAvgFloat;
                if(chargingBox.isChecked()){
                    String voltsTxt = ((EditText) findViewById(R.id.editTextVoltReading)).getText().toString();

                    double volts = Average(mapToDouble(voltsTxt.split(",")));
                    if(volts <= 13.)
                    {
                      altAmp += 16.25;
                    }
                    else if(volts > 13. && volts < 13.5)
                    {
                        altAmp += 8. ;
                    }
                    else
                    {
                        altAmp += 4.;
                    }
                }
                if(!hasSensed) {
                    getData();
                }
                SaveTextFile(path + "solar-brightness-info.txt", saveInfo + "," + ampAvg + "," + outOrIn +  "," + altAmp + "\n");
                textViewInfo.setText(info + ", Amps: " + ampAvg + ", " + outOrIn+  "," + altAmp);
                Toast.makeText(SolarLightTracker.this, "Saved", Toast.LENGTH_LONG).show();
                lightSensorDataBuffer.clear();
                hasSensed = false;
               // saveButton.setEnabled(false);
            }
        });

        findViewById(R.id.buttonSaveVolts).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String voltsTxt = ((EditText) findViewById(R.id.editTextVoltReading)).getText().toString();
                //String bampsTxt = ((EditText) findViewById(R.id.editTextBAmpReading)).getText().toString();

                //String bamps = String.valueOf(Average(mapToDouble(bampsTxt.split(","))));
                String volts = String.valueOf(Average(mapToDouble(voltsTxt.split(","))));

                Calendar c = Calendar.getInstance()  ;
                int y = c.get(Calendar.YEAR);
                int m = c.get(Calendar.MONTH) ;
                int d = c.get(Calendar.DAY_OF_MONTH) ;

                String date = y + "/" + m + "/" + d;

                SaveTextFile(path + "solar-volts-info.txt", getTime() + "," + date + "," + volts + "\n");
                Toast.makeText(SolarLightTracker.this, "Saved volts", Toast.LENGTH_LONG).show();
            }
        });

        weatherButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                locDisp.setText("Please Wait");
                (new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            fio.getForecast(String.valueOf(latitude), String.valueOf(longitude));
                            final FIOCurrently currently = new FIOCurrently(fio);
                            final FIODataPoint curweath = currently.get();
                            ccover = curweath.cloudCover();
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    locDisp.setText("Summary: " + curweath.summary() +
                                            ", Cover: " + ccover +
                                            ", Temp: " + curweath.apparentTemperature() +
                                            ", Precip Probability: " + curweath.precipProbability());
                                }
                            });
                        } catch (Exception ex) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    locDisp.setText("Download Failed");
                                }
                            });
                        }
                    }
                })).start();
            }
        });

        gpsButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (!gpsButton.isChecked()) {
                            lm.removeUpdates(locationListener);
                            gpsButton.setVisibility(View.GONE);
                            
                            findViewById(R.id.GPSInf).setVisibility(View.GONE);

                            weatherButton.setEnabled(true);
                            getDataButton.setEnabled(true);
                            SaveTextFile(path + "sol.loc.txt", latitude + "\n" + longitude);
                        } else {
                            lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
                        }
                    }
        });

        getDataButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                lightSensorDataBuffer.clear();
                hasSensed = true;
                readingSensor = true;
                textViewInfo.setText("Gathering datapoints");
            }
        });

        SensorManager mySensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        Sensor LightSensor = mySensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

        if(LightSensor != null){
            mySensorManager.registerListener(
                    LightSensorListener,
                    LightSensor,
                    SensorManager.SENSOR_DELAY_NORMAL);
        }

        File file = new File(path + "sol.loc.txt");

        if(file.exists()) {
            gpsButton.setVisibility(View.GONE);
            findViewById(R.id.GPSInf).setVisibility(View.GONE);

            try {
                ArrayList<String> lines = readAllText(path + "sol.loc.txt");
                latitude = Double.parseDouble(lines.get(0));
                longitude = Double.parseDouble(lines.get(1));
                locDisp.setText("Long: " + longitude + ", Latitude: " + latitude);
                getDataButton.setEnabled(true);
                weatherButton.setEnabled(true);
            } catch (IOException e) {
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    double getTime(){
        Calendar c = Calendar.getInstance()  ;
        double h = c.get(Calendar.HOUR_OF_DAY);
        double m = c.get(Calendar.MINUTE) / 60.0;
        double time = h + m;
        return time;
    }

    void getData() {
        double time = getTime();
        double avg = Average(lightSensorDataBuffer.toArray(new Double[lightSensorDataBuffer.size()]));
        int wk = Calendar.getInstance().get(Calendar.WEEK_OF_YEAR);

        info = "Time: " + time + ", Light Avg: " + avg +  ", Week: " + wk + ", Cloud Cover: " + ccover;
        saveInfo = time + ", " + avg +  "," + wk + "," + ccover;
        textViewInfo.setText(info);
       // saveButton.setEnabled(true);
    }

    private final SensorEventListener LightSensorListener = new SensorEventListener() {
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
                textLIGHT_reading.setText("LIGHT: " + event.values[0]);
                if (readingSensor) {
                    if (lightSensorDataBuffer.size() <= 20) {
                        lightSensorDataBuffer.add((double) event.values[0]);
                        textViewInfo.setText(textViewInfo.getText() + ".");
                    } else {
                        readingSensor = false;
                        getData();
                    }
                }
            }
        }
    };

    private final LocationListener locationListener = new LocationListener() {
        public void onLocationChanged(Location location) {
            longitude = location.getLongitude();
            latitude = location.getLatitude();

            locDisp.setText("Long: " + longitude + ", Latitude: " + latitude);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {

        }

        @Override
        public void onProviderEnabled(String provider) {

        }

        @Override
        public void onProviderDisabled(String provider) {

        }
    };

    double Average(Double[] data) {
        double sum = 0.;
        if(data.length == 0){return 0.;}
        for (double x : data) {
           sum+=x;
        }
        return sum/(double)data.length;
    }

    void SaveTextFile(String path, String txt) {
        try {
            FileWriter writer = new FileWriter(path, true);
            writer.write(txt);
            writer.flush();
            writer.close();
        }
        catch (Exception e) {
            Log.e("Exception", "File write failed: " + e.getMessage());
        }
    }

    ArrayList<String> readAllText(String fname) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(fname));
        ArrayList<String> lines = new ArrayList<>();
        String line = br.readLine();

        while (line != null) {
            lines.add(line);
            line = br.readLine();
        }
        br.close();
        return lines;
    }
}
