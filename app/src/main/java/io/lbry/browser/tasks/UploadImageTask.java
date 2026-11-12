package io.lbry.browser.tasks;

import android.os.AsyncTask;
import android.view.View;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import java.io.File;
import java.util.Locale;
import okhttp3.ResponseBody;
import okhttp3.HttpUrl;
import java.util.concurrent.TimeUnit;

import io.lbry.browser.exceptions.LbryResponseException;
import io.lbry.browser.utils.Helper;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class UploadImageTask extends AsyncTask<Void, Void, String> {
    private final String filePath;
    private final View progressView;
    private final UploadThumbnailHandler handler;
    private Exception error;

    public UploadImageTask(String filePath, View progressView, UploadThumbnailHandler handler) {
        this.filePath = filePath;
        this.progressView = progressView;
        this.handler = handler;
    }

    protected void onPreExecute() {
        Helper.setViewVisibility(progressView, View.VISIBLE);
    }
    protected String doInBackground(Void... params) {
        String thumbnailUrl = null;
        try {
            File file = new File(filePath);
            if (!file.isFile() || !file.canRead()) {
                throw new IOException("The selected image cannot be read.");
            }
            String fileName = file.getName();
            String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            String fileType = "jpg".equals(extension) ? "image/jpeg" : "image/" + extension;
            RequestBody body = new MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("upload", "Upload")
                    .addFormDataPart("file-input", fileName, RequestBody.create(file, MediaType.parse(fileType)))
                    .build();
            Request request = new Request.Builder()
                    .url("https://thumbs.odycdn.com/upload").post(body).build();
            OkHttpClient client = new OkHttpClient.Builder()
                    .callTimeout(90, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .build();
            try (Response response = client.newCall(request).execute()) {
                ResponseBody responseBody = response.body();
                if (!response.isSuccessful() || responseBody == null) {
                    throw new IOException("Image upload failed (HTTP " + response.code() + ").");
                }
                JSONObject json = new JSONObject(responseBody.string());
                if (!"success".equals(json.optString("type"))) {
                    throw new LbryResponseException("The image upload service rejected this image.");
                }
                String url = json.optString("message");
                HttpUrl imageUrl = HttpUrl.parse(url);
                if (imageUrl == null || !imageUrl.isHttps()) {
                    throw new LbryResponseException("Invalid image URL returned after upload.");
                }
                thumbnailUrl = imageUrl.toString();
            }
        } catch (IOException | JSONException | LbryResponseException ex) {
            error = ex;
        }

        return thumbnailUrl;
    }
    protected void onPostExecute(String thumbnailUrl) {
        Helper.setViewVisibility(progressView, View.GONE);
        if (handler != null) {
            if (!Helper.isNullOrEmpty(thumbnailUrl)) {
                handler.onSuccess(thumbnailUrl);
            } else {
                handler.onError(error);
            }
        }
    }

    public interface UploadThumbnailHandler {
        void onSuccess(String url);
        void onError(Exception error);
    }
}
