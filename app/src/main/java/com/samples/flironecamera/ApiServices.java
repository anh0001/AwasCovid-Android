package com.samples.flironecamera;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public interface ApiServices {
    @Multipart
    @POST("api/image/")
    Call<String> sendImage (@Part("device_id") String strDevId, @Part MultipartBody.Part photoImage, @Part MultipartBody.Part thermalImage);
}
