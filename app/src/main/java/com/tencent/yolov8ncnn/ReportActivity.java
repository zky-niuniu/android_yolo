package com.tencent.yolov8ncnn;

import android.os.Bundle;
import android.app.Activity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ArrayAdapter;
import android.util.SparseBooleanArray;
import java.util.ArrayList;
import com.amap.api.maps.AMap;
import com.amap.api.maps.MapView;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.LatLng;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import android.util.Log;
import android.content.Context;
import android.content.SharedPreferences;

public class ReportActivity extends Activity {
    private MapView mapView;
    private EditText issueDescription;
    private Button submitButton;
    private ListView issueListView;
    private ArrayAdapter<String> issueAdapter;
    private ArrayList<String> issueList = new ArrayList<>();
    private AMap aMap;
    private double currentLatitude;
    private double currentLongitude;
    private AMapLocationClient locationClient;
    private AMapLocationClientOption locationOption;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report);

        mapView = findViewById(R.id.mapView);
        issueDescription = findViewById(R.id.issueDescription);
        submitButton = findViewById(R.id.submitButton);
        issueListView = findViewById(R.id.issueListView);
        
        // 初始化列表适配器
        issueAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_multiple_choice, issueList);
        issueListView.setAdapter(issueAdapter);
        
        // 设置列表项长按事件
        issueListView.setOnItemLongClickListener((parent, view, position, id) -> {
            // 获取选中的项
            SparseBooleanArray checked = issueListView.getCheckedItemPositions();
            SharedPreferences sharedPref = getSharedPreferences("manhole_issues", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPref.edit();
            
            // 清除所有地图标记
            aMap.clear();
            
            // 遍历删除选中的项
            for (int i = issueList.size() - 1; i >= 0; i--) {
                if (checked.get(i)) {
                    // 从SharedPreferences中移除
                    editor.remove("issue_" + i + "_lat");
                    editor.remove("issue_" + i + "_lng");
                    editor.remove("issue_" + i + "_desc");
                    // 从列表中移除
                    issueList.remove(i);
                    
                    // 重新索引后续项
                    for (int j = i; j < issueList.size(); j++) {
                        String lat = sharedPref.getString("issue_" + (j+1) + "_lat", null);
                        String lng = sharedPref.getString("issue_" + (j+1) + "_lng", null);
                        String desc = sharedPref.getString("issue_" + (j+1) + "_desc", null);
                        
                        if (lat != null && lng != null && desc != null) {
                            editor.putString("issue_" + j + "_lat", lat);
                            editor.putString("issue_" + j + "_lng", lng);
                            editor.putString("issue_" + j + "_desc", desc);
                            editor.remove("issue_" + (j+1) + "_lat");
                            editor.remove("issue_" + (j+1) + "_lng");
                            editor.remove("issue_" + (j+1) + "_desc");
                        }
                    }
                }
            }
            editor.apply();
            issueAdapter.notifyDataSetChanged();
            
            // 重新添加当前位置标记
            aMap.addMarker(new MarkerOptions()
                .position(new LatLng(currentLatitude, currentLongitude))
                .title("当前位置"));
            return true;
        });

        mapView.onCreate(savedInstanceState);
        aMap = mapView.getMap();
        initLocation();

        submitButton.setOnClickListener(v -> {
            String description = issueDescription.getText().toString();
            if (!description.isEmpty()) {
                // 标记异常位置
                aMap.addMarker(new MarkerOptions()
                    .position(new LatLng(currentLatitude, currentLongitude))
                    .title(description)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                    .snippet("异常井盖报告"));
                // 调用原生方法报告异常
                Yolov8Ncnn yolov8ncnn = new Yolov8Ncnn();
                yolov8ncnn.reportManholeIssue(currentLatitude, currentLongitude, description);
                Log.d("ReportActivity", "reportManholeIssue called");
                
                // 保存异常标记到SharedPreferences
                SharedPreferences sharedPref = getSharedPreferences("manhole_issues", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPref.edit();
                // 保存到列表
                String issueInfo = "经纬度: " + currentLatitude + ", " + currentLongitude + " - " + description;
                issueList.add(issueInfo);
                issueAdapter.notifyDataSetChanged();
                
                // 保存到SharedPreferences
                int issueIndex = issueList.size() - 1;
                editor.putString("issue_" + issueIndex + "_lat", String.valueOf(currentLatitude));
                editor.putString("issue_" + issueIndex + "_lng", String.valueOf(currentLongitude));
                editor.putString("issue_" + issueIndex + "_desc", description);
                editor.apply();
            }
        });
    }

    private void initLocation() {
        try {
            locationClient = new AMapLocationClient(getApplicationContext());
        } catch (Exception e) {
            e.printStackTrace();
        }
        locationOption = new AMapLocationClientOption();
        locationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
        locationOption.setOnceLocation(false);
        locationOption.setInterval(2000);
        locationClient.setLocationOption(locationOption);
        locationClient.setLocationListener(location -> {
            if (location != null && location.getErrorCode() == 0) {
                currentLatitude = location.getLatitude();
                currentLongitude = location.getLongitude();
                // 保留异常标记，只更新当前位置
                aMap.addMarker(new MarkerOptions()
                    .position(new LatLng(currentLatitude, currentLongitude))
                    .title("当前位置"));
            }
        });
        locationClient.startLocation();
        initMap();
    }

    private void initMap() {
        // 确保地图已初始化
        if (aMap != null) {
            // 设置地图类型为普通地图
            aMap.setMapType(AMap.MAP_TYPE_NORMAL);
            
            // 添加当前位置标记
            aMap.addMarker(new MarkerOptions()
                .position(new LatLng(currentLatitude, currentLongitude))
                .title("当前位置")
                .draggable(false));
            
            // 加载并显示所有保存的异常标记
            SharedPreferences sharedPref = getSharedPreferences("manhole_issues", Context.MODE_PRIVATE);
            
            // 遍历所有保存的异常标记
            int issueCount = 0;
            while (true) {
                String lat = sharedPref.getString("issue_" + issueCount + "_lat", null);
                String lng = sharedPref.getString("issue_" + issueCount + "_lng", null);
                String desc = sharedPref.getString("issue_" + issueCount + "_desc", null);
                
                if (lat == null || lng == null || desc == null) {
                    break;
                }
                
                Log.d("ReportActivity", "Loading saved issue: " + lat + "," + lng + " - " + desc);
                aMap.addMarker(new MarkerOptions()
                    .position(new LatLng(Double.parseDouble(lat), Double.parseDouble(lng)))
                    .title(desc)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                    .snippet("异常井盖报告"));
                
                // 添加到列表
                String issueInfo = "经纬度: " + lat + ", " + lng + " - " + desc;
                if (!issueList.contains(issueInfo)) {
                    issueList.add(issueInfo);
                }
                
                issueCount++;
            }
            
            if (issueCount == 0) {
                Log.d("ReportActivity", "No saved issues found");
            } else {
                issueAdapter.notifyDataSetChanged();
            }
            
            // 移动到当前位置并设置合适的缩放级别
            aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                new LatLng(currentLatitude, currentLongitude), 17));
            
            // 启用定位图层
            aMap.setMyLocationEnabled(true);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
        if (locationClient != null) {
            locationClient.stopLocation();
            locationClient.onDestroy();
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }
}