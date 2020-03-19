package com.movtalent.app.util;

import android.app.Activity;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.kk.taurus.playerbase.config.PlayerConfig;
import com.lib.common.util.tool.StringUtil;
import com.media.playerlib.PlayApp;
import com.movtalent.app.App_Config;
import com.movtalent.app.http.HttpUtil;
import com.movtalent.app.model.dto.Param;
import com.movtalent.app.model.dto.ParamDto;

import org.litepal.LitePal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * Created by H19 on 2018/9/5 0005.
 */

public class Jiexi {

    private Activity context;
    private int mState;
    private String mvUrl; // 待解析的地址
    private int cutIndex;// 次数
    private ParseWebUrlHelper mParseHelper;
    private List<DBjk> mJklist = new ArrayList<>();
    public Jiexi init(Activity context, OnListener listener){
        this.context = context;
        this.mListener = listener;

        // ----- init 接口列表 -----------------
        mJklist = new ArrayList<>();
        if(App_Config.param != null && App_Config.param.getmJklist() != null)
            mJklist = App_Config.param.getmJklist();
        if (mJklist.size() < 2){
            mJklist.add(new DBjk("线路1","https://gege.ha123.club/wq/?url=",2));
            mJklist.add(new DBjk("线路1","http://jx.itaoju.top/?url=",2));
        }
        return this;
    };

    public void start(String t){
        if (mParseHelper != null) mParseHelper.stop();
        cutIndex = 0;
        mvUrl = t;
        start2();
    }
    private void start2(){
        String url = mJklist.get(cutIndex).getUrl() + mvUrl;
        int type = mJklist.get(cutIndex).getType();

        // 开启定时
        isEnt = false;
        int str_time = ShareSpUtils.getInt(context,"player_parse_waiting_time",10);
        Log.d("mytest","超时时间：" + str_time);
        new Handler().postDelayed(parseTime,str_time * 1000);

        if(type == 1){
            new Thread(){
                public void run(){
                    Log.d("mytest", "需要解析的URL"+ url);
                    String resStr = HttpUtil.getData(url);
                    Log.d("mytest", ""+ resStr);
                    JSONObject json = null;
                    try {
                        json = JSONObject.parseObject(resStr);
                    }catch (Exception e){

                    }
                    if(json == null || json.getString("url") == null){
                        handler.sendEmptyMessage(-1);
                        return;
                    }
                    Log.d("mytest",json.getString("url"));
                    if(json.getIntValue("success") == 1 && json.getIntValue("code") == 200){
                        String xurl = json.getString("url");
                        if(xurl.length() < 10){
                            handler.sendEmptyMessage(-1);
                            return;
                        }
                        final String xtype = json.getString("type");
                        if(json.getString("url").substring(0,2).equals("//")){
                            xurl = "http:" + xurl;
//                            PlayerConfig.setDefaultPlanId(PlayApp.PLAN_ID_MEDIA);
                        }else{
//                            PlayerConfig.setDefaultPlanId(PlayApp.PLAN_ID_EXO);
                        }
                        final String resultUrl = xurl;
                        isEnt = true;
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                mListener.ent(Jiexi.this,0,resultUrl,xtype);
                            }
                        });
                    }else{
                        Log.d("mytest",resStr);
                        handler.sendEmptyMessage(-1);
                    }
                }
            }.start();
            return;
        }

        // 开始解析，必须在ui线程中解析
        context.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d("mytest","解析URL:"+ url);
                mParseHelper = ParseWebUrlHelper.getInstance().init(context, url);
                mParseHelper.setOnParseListener(new ParseWebUrlHelper.OnParseWebUrlListener() {
                    @Override
                    public void onFindUrl(String url,final String type,Map<String, String> headers) {
                        if (type.equals("mp4") && url.contains("mp4?vkey=") && url.length() > 500){
                            url = StringUtil.getLeftText(url,".mp4?vkey=") + ".mp4";
                        }
                        final String xurl = url;
                        isEnt = true;
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                mListener.ent(Jiexi.this,0,xurl,type);
                            }
                        });

                    }
                    @Override
                    public void onError(String errorMsg) {
                        if (mParseHelper != null){
                            handler.sendEmptyMessage(-1);
                        }
                        mListener.ent(Jiexi.this,-1,errorMsg,null);
                    }
                });
                mParseHelper.startParse();
            }
        });
    }

    private boolean isEnt;

    // 解析计时
    Runnable parseTime = new Runnable() {
        @Override
        public void run() {
            if (!isEnt){
                if(mParseHelper != null)
                    mParseHelper.stop();
                mParseHelper = null;
                handler.sendEmptyMessage(-1);
            }
        }
        public void inin(){

        }
    };

    private Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what){

                case -1: // 解析失败
                    Log.d("mytest","解析失败"+ mJklist.size());
                    cutIndex = cutIndex + 1;
                    if (cutIndex < mJklist.size()){
                        start2();
                    }else{
                        mListener.ent(Jiexi.this,2,"超时",null);
                    }
                    break;
            }
            return false;
        }
    });


    private OnListener mListener;

    public void stop() {
        if (mParseHelper!=null){
            mParseHelper.stop();
            mParseHelper = null;
        }
    }

    public interface OnListener{
        void ent(Jiexi t, int errId, String msg, String type);
    }



    private int STATE_ING = 1; // 进行中
    private int STATE_FINISH = 2; // 完成
    private int STATE_STOP = 3; // 停止
    private int STATE_ERROR = 4; // 错误

    private String parseSuffix(String url) {
        String con = url;
        String t1 = StringUtil.getTextRight(con,".");
        if (t1.length()< 5) return t1;

        t1 = StringUtil.getLeftText(url,"?");
        if (t1 != null) con = t1;

        t1 = StringUtil.getTextRight(con,".");
        if (t1 == null || t1.length() > 5) t1 = StringUtil.getTextRight(con,"/");

        con = t1;
        return con;
    }

}
