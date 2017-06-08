package com.example.prades.watchout;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;


public class MainActivity extends AppCompatActivity {
    Button button;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getSupportActionBar().hide();

        button = (Button)findViewById(R.id.button);

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(button.getText().toString() == "END") {
                    button.setText("START");
                    Intent intent = new Intent(MainActivity.this,MainService.class); // 이동할 컴포넌트
                    stopService(intent); // 서비스 종료
                }
                else {
                    button.setText("END");
                    Intent intent = new Intent(MainActivity.this,MainService.class);
                    startService(intent);
                }
            }
        });
    }
}