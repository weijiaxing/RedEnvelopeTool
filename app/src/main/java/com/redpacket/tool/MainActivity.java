package com.redpacket.tool;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 主界面类
 * 负责引导用户开启无障碍服务和电池优化白名单
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnAccessibility = findViewById(R.id.btn_accessibility);
        Button btnBattery = findViewById(R.id.btn_battery);

        // 跳转至无障碍设置
        btnAccessibility.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });

        // 检查并引导忽略电池优化
        btnBattery.setOnClickListener(v -> {
            if (isIgnoringBatteryOptimizations()) {
                Toast.makeText(this, "已加入电池优化白名单", Toast.LENGTH_SHORT).show();
            } else {
                requestIgnoreBatteryOptimizations();
            }
        });
    }

    /**
     * 检查是否已忽略电池优化
     */
    private boolean isIgnoringBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            return powerManager.isIgnoringBatteryOptimizations(getPackageName());
        }
        return true;
    }

    /**
     * 请求忽略电池优化
     */
    private void requestIgnoreBatteryOptimizations() {
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            // 如果跳转失败，尝试进入电池设置页面
            Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            startActivity(intent);
        }
    }
}
