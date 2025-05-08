// Tencent is pleased to support the open source community by making ncnn available.
//
// Copyright (C) 2021 THL A29 Limited, a Tencent company. All rights reserved.
//
// Licensed under the BSD 3-Clause License (the "License"); you may not use this file except
// in compliance with the License. You may obtain a copy of the License at
//
// https://opensource.org/licenses/BSD-3-Clause
//
// Unless required by applicable law or agreed to in writing, software distributed
// under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
// CONDITIONS OF ANY KIND, either express or implied. See the License for the
// specific language governing permissions and limitations under the License.

package com.tencent.yolov8ncnn;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.Spinner;

import android.content.Intent;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import com.amap.api.maps.MapsInitializer;
import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import android.app.AlertDialog;



public class MainActivity extends Activity implements SurfaceHolder.Callback, View.OnClickListener
{
    public static final int REQUEST_CAMERA = 100;
    public static final int REQUEST_LOCATION = 101;

    private Yolov8Ncnn yolov8ncnn = new Yolov8Ncnn();
    private int facing = 0;

    private Spinner spinnerModel;
    private Spinner spinnerCPUGPU;
    private int current_model = 0;
    private int current_cpugpu = 0;

    private SurfaceView cameraView;
private AMapLocationClient locationClient;
private AMapLocationClientOption locationOption;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        
        // 高德地图SDK隐私合规初始化
        //MapsInitializer.updatePrivacyShow(getApplicationContext(), true, true);
        //MapsInitializer.updatePrivacyAgree(getApplicationContext(), true);
        AMapLocationClient.updatePrivacyShow(getApplicationContext(),true,true);
        AMapLocationClient.updatePrivacyAgree(getApplicationContext(),true);
        setContentView(R.layout.main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        cameraView = (SurfaceView) findViewById(R.id.cameraview);

        cameraView.getHolder().setFormat(PixelFormat.RGBA_8888);
        cameraView.getHolder().addCallback(this);

        Button buttonSwitchCamera = (Button) findViewById(R.id.buttonSwitchCamera);
        buttonSwitchCamera.setOnClickListener(this);
        
        Button buttonReportIssue = (Button) findViewById(R.id.buttonReportIssue);
        buttonReportIssue.setOnClickListener(this);

        spinnerModel = (Spinner) findViewById(R.id.spinnerModel);
        spinnerModel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long id)
            {
                if (position != current_model)
                {
                    current_model = position;
                    reload();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0)
            {
            }
        });

        spinnerCPUGPU = (Spinner) findViewById(R.id.spinnerCPUGPU);
        spinnerCPUGPU.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long id)
            {
                if (position != current_cpugpu)
                {
                    current_cpugpu = position;
                    reload();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0)
            {
            }
        });

        reload();
    }

    private void reload()
    {
        boolean ret_init = yolov8ncnn.loadModel(getAssets(), current_model, current_cpugpu);
        if (!ret_init)
        {
            Log.e("MainActivity", "yolov8ncnn loadModel failed");
        }
    }
    
    private void requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 
                REQUEST_LOCATION);
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限已授予
            }
        }
    }
    
    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.buttonSwitchCamera) {
            int new_facing = 1 - facing;
            yolov8ncnn.closeCamera();
            yolov8ncnn.openCamera(new_facing);
            facing = new_facing;
        } else if (v.getId() == R.id.buttonReportIssue) {
            reportManholeCoverIssue();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
    {
        yolov8ncnn.setOutputWindow(holder.getSurface());
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder)
    {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder)
    {
    }

    @Override
    public void onResume()
    {
        super.onResume();

        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED)
        {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA}, REQUEST_CAMERA);
        }
        
        requestLocationPermission();
        yolov8ncnn.openCamera(facing);
        initLocation();
    }
    
    private void initLocation() {
        try {
            // 检查定位权限
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.e("Location", "缺少定位权限");
                return;
            }
            
            // 检查设备定位服务是否开启
            android.location.LocationManager lm = (android.location.LocationManager)getSystemService(LOCATION_SERVICE);
            if (!lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
                Log.e("Location", "设备定位服务未开启");
                // 提示用户开启定位服务
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage("请开启定位服务");
                builder.setPositiveButton("设置", (dialog, which) -> {
                    Intent intent = new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    startActivity(intent);
                });
                builder.setNegativeButton("取消", null);
                builder.show();
                return;
            }
            
            locationClient = new AMapLocationClient(getApplicationContext());
            locationOption = new AMapLocationClientOption();
            // 设置为高精度定位模式
            locationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
            // 设置为单次定位
            locationOption.setOnceLocation(true);
            // 设置定位间隔
            locationOption.setInterval(2000);
            // 设置是否强制刷新WIFI，默认为强制刷新
            locationOption.setWifiScan(true);
            // 设置是否允许模拟位置,默认为false，不允许模拟位置
            locationOption.setMockEnable(false);
            // 设置定位请求超时时间
            locationOption.setHttpTimeOut(20000);
            
            locationClient.setLocationOption(locationOption);
            locationClient.setLocationListener(location -> {
                if (location != null && location.getErrorCode() == 0) {
                    double latitude = location.getLatitude();
                    double longitude = location.getLongitude();
                    Log.d("Location", "Latitude: " + latitude + " Longitude: " + longitude);
                    // 在这里可以更新UI显示当前位置
                } else {
                    Log.e("Location", "定位失败,错误码:" + location.getErrorCode() + ", 错误信息:" + location.getErrorInfo());
                }
            });
            locationClient.startLocation();
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("Location", "初始化定位失败:" + e.getMessage());
        }
    }

    private void reportManholeCoverIssue() {
        Intent intent = new Intent(this, ReportActivity.class);
        startActivity(intent);
    }

    @Override
    public void onPause()
    {
        super.onPause();

        yolov8ncnn.closeCamera();
        if (locationClient != null) {
            locationClient.stopLocation();
            locationClient.onDestroy();
        }
    }
}
