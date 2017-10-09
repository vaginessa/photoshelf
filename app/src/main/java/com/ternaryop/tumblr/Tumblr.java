package com.ternaryop.tumblr;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

@SuppressWarnings({"UnusedReturnValue", "unused"})
public class Tumblr {
    public final static int MAX_POST_PER_REQUEST = 20;
    private final static String API_PREFIX = "http://api.tumblr.com/v2";

    private static Tumblr instance;
    
    private final TumblrHttpOAuthConsumer consumer;

    private Tumblr(TumblrHttpOAuthConsumer consumer) {
        this.consumer = consumer;
    }

    public static Tumblr getSharedTumblr(Context context) {
        if (instance == null) {
            instance = new Tumblr(new TumblrHttpOAuthConsumer(context));
        }
        return instance;
    }
    
    public static boolean isLogged(Context context) {
        return TumblrHttpOAuthConsumer.isLogged(context);
    }

    public static void login(Context context) {
        TumblrHttpOAuthConsumer.loginWithActivity(context);
    }

    public static void logout(Context context) {
        TumblrHttpOAuthConsumer.logout(context);
    }
    
    public static boolean handleOpenURI(final Context context, final Uri uri, AuthenticationCallback callback) {
        return TumblrHttpOAuthConsumer.handleOpenURI(context, uri, callback);
    }
    
    public Blog[] getBlogList() {
        String apiUrl = API_PREFIX + "/user/info";

        try {
            JSONObject json = consumer.jsonFromGet(apiUrl);
            JSONArray jsonBlogs = json.getJSONObject("response").getJSONObject("user").getJSONArray("blogs");
            Blog[] blogs = new Blog[jsonBlogs.length()];
            for (int i = 0; i < jsonBlogs.length(); i++) {
                blogs[i] = new Blog(jsonBlogs.getJSONObject(i));
            }
            return blogs;
        } catch (Exception e) {
            throw new TumblrException(e);
        }
    }

    public String getApiUrl(String tumblrName, String suffix) {
        return API_PREFIX + "/blog/" + tumblrName + ".tumblr.com" + suffix;
    }
    
    public void draftPhotoPost(final String tumblrName, final Uri uri, final String caption, final String tags) {
        try {
            createPhotoPost(tumblrName, uri, caption, tags, "draft");
        } catch (JSONException e) {
            throw new TumblrException(e);
        }
    }

    public void publishPhotoPost(final String tumblrName, final Uri uri, final String caption, final String tags) {
        try {
            createPhotoPost(tumblrName, uri, caption, tags, "published");
        } catch (JSONException e) {
            throw new TumblrException(e);
        }
    }
    
    protected long createPhotoPost(final String tumblrName, final Uri uri, final String caption, final String tags, final String state) throws JSONException {
        String apiUrl = getApiUrl(tumblrName, "/post");
        HashMap<String, Object> params = new HashMap<>();

        if (uri.getScheme().equals("file")) {
            params.put("data", new File(uri.getPath()));
        } else {
            params.put("source", uri.toString());
        }
        params.put("state", state);
        params.put("type", "photo");
        params.put("caption", caption);
        params.put("tags", tags);
        
        JSONObject json = consumer.jsonFromPost(apiUrl, params);
        return json.getJSONObject("response").getLong("id");
    }

    public void draftTextPost(final String tumblrName, final String title, final String body, final String tags) {
        try {
            createTextPost(tumblrName, title, body, tags, "draft");
        } catch (JSONException e) {
            throw new TumblrException(e);
        }
    }

    public void publishTextPost(final String tumblrName, final String title, final String body, final String tags) {
        try {
            createTextPost(tumblrName, title, body, tags, "published");
        } catch (JSONException e) {
            throw new TumblrException(e);
        }
    }

    protected long createTextPost(final String tumblrName, final String title, final String body, final String tags, final String state) throws JSONException {
        String apiUrl = getApiUrl(tumblrName, "/post");
        HashMap<String, Object> params = new HashMap<>();

        params.put("state", state);
        params.put("type", "text");
        params.put("title", title);
        params.put("body", body);
        params.put("tags", tags);

        JSONObject json = consumer.jsonFromPost(apiUrl, params);
        return json.getJSONObject("response").getLong("id");
    }

    public static void addPostsToList(ArrayList<TumblrPost> list, JSONArray arr) throws JSONException {
        for (int i = 0; i < arr.length(); i++) {
            list.add(build(arr.getJSONObject(i)));
        }
    }
    
    public List<TumblrPost> getDraftPosts(final String tumblrName, long maxTimestamp) {
        String apiUrl = getApiUrl(tumblrName, "/posts/draft");
        ArrayList<TumblrPost> list = new ArrayList<>();

        try {
            JSONObject json = consumer.jsonFromGet(apiUrl);
            JSONArray arr = json.getJSONObject("response").getJSONArray("posts");
            
            Map<String, String> params = new HashMap<>(1);
            while (arr.length() > 0) {
                for (int i = 0; i < arr.length(); i++) {
                    TumblrPost post = build(arr.getJSONObject(i));
                    if (post.getTimestamp() <= maxTimestamp) {
                        return list;
                    }
                    list.add(post);
                }
                long beforeId = arr.getJSONObject(arr.length() - 1).getLong("id");
                params.put("before_id", beforeId + "");

                arr = consumer.jsonFromGet(apiUrl, params).getJSONObject("response").getJSONArray("posts");
            }
        } catch (Exception e) {
            throw new TumblrException(e);
        }
        return list;
    }

    public List<TumblrPost> getQueue(final String tumblrName, Map<String, String> params) {
        String apiUrl = getApiUrl(tumblrName, "/posts/queue");
        ArrayList<TumblrPost> list = new ArrayList<>();

        try {
            JSONObject json = consumer.jsonFromGet(apiUrl, params);
            JSONArray arr = json.getJSONObject("response").getJSONArray("posts");
            addPostsToList(list, arr);
        } catch (Exception e) {
            throw new TumblrException(e);
        }
        return list;
    }
    
    public List<TumblrPhotoPost> getPhotoPosts(final String tumblrName, Map<String, String> params) {
        String apiUrl = getApiUrl(tumblrName, "/posts/photo");
        ArrayList<TumblrPhotoPost> list = new ArrayList<>();

        try {
            Map<String, String> paramsWithKey = new HashMap<>(params);
            paramsWithKey.put("api_key", consumer.getConsumerKey());

            JSONObject json = consumer.jsonFromGet(apiUrl, paramsWithKey);
            JSONArray arr = json.getJSONObject("response").getJSONArray("posts");
            long totalPosts = json.getJSONObject("response").has("total_posts") ? json.getJSONObject("response").getLong("total_posts") : -1; 
            for (int i = 0; i < arr.length(); i++) {
                TumblrPhotoPost post = (TumblrPhotoPost)build(arr.getJSONObject(i));
                if (totalPosts != -1) {
                    post.setTotalPosts(totalPosts);
                }
                list.add(post);
            }
        } catch (Exception e) {
            throw new TumblrException(e);
        }

        return list;
    }

    public long publishPost(final String tumblrName, final long id) {
        String apiUrl = getApiUrl(tumblrName, "/post/edit");
        
        Map<String, String> params = new HashMap<>();
        params.put("id", String.valueOf(id));
        params.put("state", "published");

        try {
            JSONObject jsonObject = consumer.jsonFromPost(apiUrl, params);
            JSONObject response = jsonObject.getJSONObject("response");
            return response.getLong("id");
        } catch (Exception e) {
            throw new TumblrException(e);
        }
    }

    public void deletePost(final String tumblrName, final long id) {
        String apiUrl = getApiUrl(tumblrName, "/post/delete");
      
        Map<String, String> params = new HashMap<>();
        params.put("id", String.valueOf(id));

        try {
            consumer.jsonFromPost(apiUrl, params);
        } catch (Exception e) {
            throw new TumblrException(e);
        }
    }

    public void saveDraft(final String tumblrName, final long id) {
        String apiUrl = getApiUrl(tumblrName, "/post/edit");
        
        Map<String, String> params = new HashMap<>();
        params.put("id", String.valueOf(id));
        params.put("state", "draft");

        try {
            consumer.jsonFromPost(apiUrl, params);
        } catch (Exception e) {
            throw new TumblrException(e);
        }
    }

    public long schedulePost(final String tumblrName, final TumblrPost post, final long timestamp) {
        try {
            String apiUrl = getApiUrl(tumblrName, "/post/edit");
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US);
            String gmtDate = dateFormat.format(new Date(timestamp));

            Map<String, String> params = new HashMap<>();
            params.put("id", post.getPostId() + "");
            params.put("state", "queue");
            params.put("publish_on", gmtDate);

            if (post instanceof TumblrPhotoPost) {
                params.put("caption", ((TumblrPhotoPost) post).getCaption());
            }
            params.put("tags", post.getTagsAsString());

            return consumer.jsonFromPost(apiUrl, params).getJSONObject("response").getLong("id");
        } catch (Exception e) {
            throw new TumblrException(e);
        }
    }

    public void editPost(final String tumblrName, final Map<String, String> params) {
        if (!params.containsKey("id")) {
            throw new TumblrException("The id is mandatory to edit post");
        }
        String apiUrl = getApiUrl(tumblrName, "/post/edit");

        consumer.jsonFromPost(apiUrl, params);
    }

    public List<TumblrPost> getPublicPosts(final String tumblrName, Map<String, String> params) {
        String apiUrl = getApiUrl(tumblrName, "/posts" + getPostTypeAsUrlPath(params));

        Map<String, String> modifiedParams = new HashMap<>(params);
        modifiedParams.remove("type");

        try {
            JSONObject json = consumer.publicJsonFromGet(apiUrl, modifiedParams);
            JSONArray jsonArray = json.getJSONObject("response").getJSONArray("posts");
            ArrayList<TumblrPost> list = new ArrayList<>();
            for (int i = 0; i < jsonArray.length(); i++) {
                list.add(build(jsonArray.getJSONObject(i)));
            }
            return list;
        } catch (JSONException e) {
            throw new TumblrException(e);
        }
    }

    /**
     * Return the post type contained into params (if any) prepended by "/" url path separator
     * @param params API params
     * @return the "/" + type or empty string if not present
     */
    private String getPostTypeAsUrlPath(Map<String, String> params) {
        String type = params.get("type");
        if (type == null || type.trim().isEmpty()) {
            return "";
        }
        return "/" + type;
    }

    public TumblrFollowers getFollowers(final String tumblrName, final Map<String, String> params, final TumblrFollowers followers) {
        String apiUrl = getApiUrl(tumblrName, "/followers");

        Map<String, String> modifiedParams = new HashMap<>();
        if (params != null) {
            modifiedParams.putAll(params);
        }
        modifiedParams.put("base-hostname", tumblrName + ".tumblr.com");

        try {
            JSONObject json = consumer.jsonFromGet(apiUrl, modifiedParams);
            TumblrFollowers resultFollowers = followers;
            if (resultFollowers == null) {
                resultFollowers = new TumblrFollowers();
            }
            resultFollowers.add(json.getJSONObject("response"));

            return resultFollowers;
        } catch (Exception e) {
            throw new TumblrException(e);
        }
    }

    public TumblrFollowers getFollowers(final String tumblrName, final int offset, final int limit, final TumblrFollowers followers) {
        Map<String, String> params = new HashMap<>();
        params.put("offset", Integer.toString(offset));
        params.put("limit", Integer.toString(limit));

        return getFollowers(tumblrName, params, followers);
    }

    public static TumblrPost build(JSONObject json) throws JSONException {
        String type = json.getString("type");
        
        if (type.equals("photo")) {
            return new TumblrPhotoPost(json);
        }
        throw new IllegalArgumentException("Unable to build post for type " + type);
    }

    public TumblrHttpOAuthConsumer getConsumer() {
        return consumer;
    }
}
