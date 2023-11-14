package com.example.appdetector;

import androidx.appcompat.app.AppCompatActivity;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import org.eclipse.paho.client.mqttv3.*;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private EditText brokerAddressEditText;
    private EditText brokerPortEditText;
    private EditText gasTopicEditText;
    private EditText thresholdEditText;
    private TextView gasValueTextView,textItem,nameValue;
    private ListView listView;
    private ArrayAdapter<String> arrayAdapter;
    private ArrayList<String> gasValuesList = new ArrayList<>();
    private SharedPreferences prefs;
    private MqttClient mqttClient;
    private double thresholdValue = 0.0;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        brokerAddressEditText = findViewById(R.id.brokerAddressEditText);
        brokerPortEditText = findViewById(R.id.brokerPortEditText);
        gasTopicEditText = findViewById(R.id.gasTopicEditText);
        thresholdEditText = findViewById(R.id.thresholdEditText);
        gasValueTextView = findViewById(R.id.gasValueTextView);
        listView = findViewById(R.id.list_id);

        arrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, gasValuesList);
        listView.setAdapter(arrayAdapter);
        Button startButton = findViewById(R.id.startButton);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Set the threshold value
                String thresholdStr = thresholdEditText.getText().toString();
                if (!thresholdStr.isEmpty()) {
                    thresholdValue = Double.parseDouble(thresholdStr);
                }

                // Connect to MQTT broker
                startButtonClick(view);
            }
        });
    }
    private void connectToMQTTBroker() {
        String brokerAddress = brokerAddressEditText.getText().toString();
        String brokerPort = brokerPortEditText.getText().toString();
        final String broker = "tcp://" + brokerAddress + ":" + brokerPort;
        final String clientId = "AndroidCl" + System.currentTimeMillis();

        try {
            mqttClient = new MqttClient(broker, clientId, null);

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);

            mqttClient.connect(options);

            mqttClient.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    // Handle connection lost
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    // Handle incoming messages
                    if (topic.equals(gasTopicEditText.getText().toString())) {
                        String gasValueStr = new String(message.getPayload());
                        displayGasValue(gasValueStr);

                        // Check if the gas value exceeds the threshold
                        double gasValue = Double.parseDouble(gasValueStr);
                        if (gasValue > thresholdValue) {
                            handleGasExceedingThreshold(gasValue);
                        }
                        gasValuesList.add(gasValueStr);
                        updateGasValuesList();
                    }
                }
                private void updateGasValuesList() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // Notify the ArrayAdapter that the underlying data has changed
                            arrayAdapter.notifyDataSetChanged();
                        }
                    });
                }
                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // Not used in this example
                }
            });

            mqttClient.subscribe(gasTopicEditText.getText().toString(), 0);

        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void displayGasValue(final String value) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                gasValueTextView.setText(value);
                Toast.makeText(getApplicationContext(), value, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void handleGasExceedingThreshold(final double gasValue) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (gasValue > thresholdValue) {
                    // Vibrate the device
                    vibrateDevice();
                }
            }
        });
    }
    private void vibrateDevice() {
        // Get the Vibrator service
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        // Check if the device supports vibration
        if (vibrator != null && vibrator.hasVibrator()) {
            // Vibrate for 5000 milliseconds (5 seconds)
            vibrator.vibrate(5000);
        }
    }

    private void showGasExceedingThresholdNotification(double gasValue) {// Create a notification channel if the device is running Android Oreo or higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelId = "GasNotificationChannel";
            CharSequence channelName = "Gas Notifications";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(channelId, channelName, importance);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }

        // Create the notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "GasNotificationChannel")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Gas Alert!")
                .setContentText("Gas value exceeds threshold: " + gasValue)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

// Show the notification
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        notificationManager.notify(1, builder.build());
    }

    public void startButtonClick(View view) {
        // Set the threshold value
        String thresholdStr = thresholdEditText.getText().toString();
        if (!thresholdStr.isEmpty()) {
            thresholdValue = Double.parseDouble(thresholdStr);
        }

        // Connect to MQTT broker
        connectToMQTTBroker();

        // Optionally, you can add additional logic here if needed
        // For example, you might want to update the UI or perform other actions.
    }
}