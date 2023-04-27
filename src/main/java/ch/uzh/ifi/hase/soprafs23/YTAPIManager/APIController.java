package ch.uzh.ifi.hase.soprafs23.YTAPIManager;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Duration;
import java.util.Date;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import org.hibernate.type.StringNVarcharType;
import org.springframework.boot.json.GsonJsonParser;
import org.springframework.data.util.Pair;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import ch.uzh.ifi.hase.soprafs23.YTAPIManager.CommentList.Item.Snippet;
import ch.uzh.ifi.hase.soprafs23.entity.Comment;
import ch.uzh.ifi.hase.soprafs23.game.Correctness;
import ch.uzh.ifi.hase.soprafs23.game.Hand;
import ch.uzh.ifi.hase.soprafs23.game.VideoData;


class APIController {

    public static void main(String[] args) throws Exception {
        // String response = APICaller.getVideoInfoByVideoId("5yx6BWlEVcY");
        // System.out.println(response);

        // try (PrintWriter out = new PrintWriter("src/main/resources/responseJson.txt")) {
        //     out.println(response);
        // }

        // String response = APICaller.getVideosByPlaylistId("PLbZIPy20-1pN7mqjckepWF78ndb6ci_qi");
        // System.out.println(response);

        // try (PrintWriter out = new PrintWriter("src/main/resources/responseJson.txt")) {
        //     out.println(response);
        // }
        
        // var happy_try = getGameDataByPlaylist("PLbZIPy20-1pN7mqjckepWF78ndb6ci_qi", Language.GERMAN);
        // var happy = new GsonVDandHand(happy_try);

        // Gson gson = new GsonBuilder().setDateFormat(DateFormat.FULL, DateFormat.FULL).create();
        // String response = gson.toJson(happy);
        // try (PrintWriter out = new PrintWriter("src/main/resources/responseJson.txt")) {
        //     out.println(response);
        // }
    }

    static Pair<VideoData, List<Hand>> readFromFile(String path) throws JsonSyntaxException, FileNotFoundException, IOException {
        Gson gson = new GsonBuilder().setDateFormat(DateFormat.FULL, DateFormat.FULL).create();
        return gson.fromJson(APIController.readFile(path), GsonVDandHand.class).get();
    }

    //this is the complete function :)
    static Pair<VideoData, List<Hand>> getGameDataByQuery(String query, Language language)
            throws IOException, InterruptedException, Exception {
        return getGameDataFromVidsAndComments(
                collectCommentsFromVideoList(
                        fromJsonToVideoList(
                                APICaller.getVideosByQuery(query, language))));
    }

    static Pair<VideoData, List<Hand>> getGameDataByPlaylist(String playlistId, Language language)
            throws IOException, InterruptedException, Exception {
        return getGameDataFromVidsAndComments(
                collectCommentsFromVideoList(
                        fromJsonToPlaylistVideoList(
                                APICaller.getVideosByPlaylistId(playlistId)).toVideoList()));
    }

    static Integer getVideoCountForPlaylist(String playlistId) throws IOException, InterruptedException {
        var videoList = fromJsonToVideoList(APICaller.getVideosByPlaylistId(playlistId));
        return videoList.items.size();
    }
    

    // This function mutates the input!! The input is generated by the collectComments from video list function.
    // Returns data ready to use for the game.
    private static Pair<VideoData, List<Hand>> getGameDataFromVidsAndComments(//long and messy function :(
            List<Pair<VideoList.Item, List<CommentList.Item>>> listOfVidsAndComments) throws Exception {
        Random rand = new Random();
        Pair<VideoList.Item, List<CommentList.Item>> theChosen = listOfVidsAndComments
                .get(rand.nextInt(listOfVidsAndComments.size()));
        listOfVidsAndComments = new ArrayList<>(listOfVidsAndComments); //shallow copy
        if (!listOfVidsAndComments.remove(theChosen)) {
            throw new Exception("Somehow removing the Chosen did not work");
        }
        List<CommentList.Item> theRest = new ArrayList<>();
        for (Pair<VideoList.Item, List<CommentList.Item>> p : listOfVidsAndComments) {
            theRest.addAll(p.getSecond());
        }
        //theChosen and theRest is now defined
        //now lets create the videoData
        var video = theChosen.getFirst();
        var chosenComments = theChosen.getSecond();

        var jsonStringVidInfo = APICaller.getVideoInfoByVideoId(video.id.videoId);
        var videoInfoList = fromJsonVideoInfoList(jsonStringVidInfo);

        var statistics = videoInfoList.items.get(0).statistics;
        var contentDetails = videoInfoList.items.get(0).contentDetails;
        VideoData videoData = new VideoData(statistics.viewCount, statistics.likeCount, video.snippet.title,
                video.snippet.thumbnails.medium.url, video.snippet.publishedAt,
                java.time.Duration.parse(contentDetails.duration));

        //now lets create the 6 player hands
        List<Hand> hands = new ArrayList<>();
        theRest.get(0).toComment();
        for (int i = 0; i < 7; i++) { //seven hands with each six comments
            var hand = new HandCreator();
            for (int n = i; n < 6; n++) {//selecting correct comments
                CommentList.Item c = chosenComments.get(rand.nextInt(chosenComments.size()));
                chosenComments.remove(c); //removing the comments from the list so no two player have the same comments
                hand.addComment(c.toComment(), Correctness.CORRECT);
            }
            for (int n = i; n > 0; n--) {//selecting wrong comments
                CommentList.Item c = theRest.get(rand.nextInt(theRest.size()));
                theRest.remove(c); //removing the comments from the list so no two player have the same comments
                hand.addComment(c.toComment(), Correctness.WRONG);
            }
            hands.add(new Hand(hand.getComments())); //creating the immutable Hand object from the Hand creator
        }

        return Pair.of(videoData, hands);
    }

    

    //takes a list of videos filters to videos with comment and fetches 100 most relevant comments filters them to be longer than 50 chars.
    private static List<Pair<VideoList.Item, List<CommentList.Item>>> collectCommentsFromVideoList(VideoList videoList) {
        List<Pair<VideoList.Item, List<CommentList.Item>>> videosWithComments = new ArrayList<>();
        final int MINCHARS = 50;

        for (VideoList.Item video : videoList.items) {

            CommentList commentList;
            try {
                String json = APICaller.getCommentsByVideoId(video.id.videoId);
                commentList = fromJsonToCommentList(json);
                commentList = filterCommentByLength(commentList, MINCHARS);

                if (commentList.items.size() < 21) {
                    throw new Exception("Not enough (21 or more) comments longer than " + MINCHARS + " chars.");
                } else {
                    System.out.println("Successfully added video Comments");
                }
                Pair<VideoList.Item, List<CommentList.Item>> vac = Pair.of(video, commentList.items);
                videosWithComments.add(vac);
            } catch (Exception e) {
                System.out.println("Problem in fetching comments: " + e);
            }
        }

        return videosWithComments;
    }

    private static CommentList filterCommentByLength(CommentList cl, int minimumChars) {
        var listOfCommentItems = new ArrayList<CommentList.Item>(cl.items); // shallow copy
        for (CommentList.Item i : cl.items) {
            if (i.snippet.topLevelComment.snippet.textDisplay.length() < minimumChars) {
                listOfCommentItems.remove(i);
            }
        }
        cl.items = listOfCommentItems;
        return cl;
    }

    static String readFile(String path) throws FileNotFoundException, IOException {
        String jsonString = "";
        try (FileReader in = new FileReader(path)) {
            int c;
            while ((c = in.read()) != -1)
                jsonString += (char) c;
            in.close();
        }
        return jsonString;
    }

    static VideoList fromJsonToVideoList(String jsonString) {
        jsonString = jsonString.replace("default", "default_escape");

        Gson gson = new GsonBuilder().setDateFormat(DateFormat.FULL, DateFormat.FULL).create();
        return gson.fromJson(jsonString, VideoList.class);
    }

    static PlaylistVideoList fromJsonToPlaylistVideoList(String jsonString) {
        jsonString = jsonString.replace("default", "default_escape");

        Gson gson = new GsonBuilder().setDateFormat(DateFormat.FULL, DateFormat.FULL).create();
        return gson.fromJson(jsonString, PlaylistVideoList.class);
    }

    static CommentList fromJsonToCommentList(String jsonString) throws Exception {
        ErrorJson err = new Gson().fromJson(jsonString, ErrorJson.class);
        if (err.error != null) {
            throw new Exception(err.error.message);
        }
        Gson gson = new GsonBuilder().setDateFormat(DateFormat.FULL, DateFormat.FULL).create();
        return gson.fromJson(jsonString, CommentList.class);
    }

    static VideoInfoList fromJsonVideoInfoList(String jsonString) {
        Gson gson = new GsonBuilder().setDateFormat(DateFormat.FULL, DateFormat.FULL).create();
        return gson.fromJson(jsonString, VideoInfoList.class);
    }
}

class Test {
    public static void main(String[] args) throws Exception {
        VideoList videoList = APIController.fromJsonToVideoList(APIController.readFile("src/main/resources/VideoByQueryJson.txt"));

        CommentList c = APIController.fromJsonToCommentList(APIController.readFile("src/main/resources/CommentsByVideoIdJson.txt"));

        VideoInfoList vil = APIController.fromJsonVideoInfoList(APIController.readFile("src/main/resources/VideoInfoJson.txt"));
    }
}

//Java object mimicking the json response of the search query
class VideoList {

    //public String kind;
    //public String etag;
    //public String nextPageToken;
    //public String regionCode;
    //public PageInfo pageInfo;
    public List<Item> items;

    public class PageInfo {
        public int totalResults;
        public int resultsPerPage;
    }

    public class Item {

        //public String kind;
        //public String etag;
        public itemId id;

        public class itemId {
            //public String kind;
            public String videoId;
        }

        public Snippet snippet;

        public class Snippet {

            public Date publishedAt;
            //public String channelId;
            public String title;
            //public String description;
            public Thumbnails thumbnails;

            public class Thumbnails {
                //public Thumbnail default_escape;
                public Thumbnail medium;
                //public Thumbnail high;

                public class Thumbnail {
                    public String url;
                    //public int width;
                    //public int height;
                }

                //public String channelTitle;
                //public String liveBroadcastContent;
                //public Time publishTime;
            }
        }
    }
}

class PlaylistVideoList {
    public VideoList toVideoList() {
        var vd = new VideoList();
        vd.items = new ArrayList<>();
        for (var i : items) {
            vd.items.add(i.toVideoList());
        }
        return vd;
    }

    //public String kind;
    //public String etag;
    //public String nextPageToken;
    //public String regionCode;
    //public PageInfo pageInfo;
    public List<Item> items;

    // public class PageInfo {
    //     public int totalResults;
    //     public int resultsPerPage;
    // }

    public class Item {

        //public String kind;
        //public String etag;
        // public String id;

        public Snippet snippet;

        public class Snippet {

            public Date publishedAt;
            //public String channelId;
            public String title;
            //public String description;
            public Thumbnails thumbnails;

            public class Thumbnails {
                //public Thumbnail default_escape;
                public Thumbnail medium;
                //public Thumbnail high;

                public class Thumbnail {
                    public String url;
                    //public int width;
                    //public int height;
                }

                //public String channelTitle;
                //public String liveBroadcastContent;
                //public Time publishTime;
            }

            ResourceId resourceId;
            public class ResourceId {
                //String kind;
                String videoId;
            }

            public VideoList.Item.Snippet toVideoList(VideoList.Item i) {
                var s = i.new Snippet();
                s.publishedAt = publishedAt;
                s.thumbnails = s.new Thumbnails();
                s.thumbnails.medium = s.thumbnails.new Thumbnail();
                s.thumbnails.medium.url = thumbnails.medium.url;
                s.title = title;
                return s;
            }
        }

        public VideoList.Item toVideoList() {
            var i = new VideoList().new Item();
            i.id = i.new itemId();
            i.id.videoId = snippet.resourceId.videoId;
            i.snippet = snippet.toVideoList(i);
            return i;
        }
    }
}

//Java object which models the Json object received after a YT API CommentThreads call
class CommentList{

    //public String kind;
    // public String etag;
    // public String nextPageToken;
    public List<Item> items;

    // public PageInfo pageInfo;

    public class PageInfo {
        public int totalResults;
        public int resultsPerPage;
    }

    public class Item {
        public Comment toComment() {
            var cl = this;
            var c = new Comment(cl.id, cl.snippet.videoId, cl.snippet.topLevelComment.snippet.textDisplay, cl.snippet.topLevelComment.snippet.authorDisplayName, 
            cl.snippet.topLevelComment.snippet.likeCount, cl.snippet.topLevelComment.snippet.publishedAt);
            return c;
        }

        // public String kind;
        // public String etag;
        public String id;
        public Snippet snippet;

        class Snippet {
            public String videoId;
            public TopLevelComment topLevelComment;

            class TopLevelComment {

                // public String kind;
                // public String etag;
                public String id;
                public Snippet_2 snippet;

                class Snippet_2 {
                    // public String videoId;
                    public String textDisplay;
                    // public String textOriginal;
                    public String authorDisplayName;
                    // public String authorProfileImageUrl;
                    // public String authorChannelUrl;
                    // public AuthorChannelId authorChannelId;

                    class AuthorChannelId {
                        public String value;
                    }

                    // public boolean canRate;
                    // public String viewerRating;
                    public int likeCount;
                    public Date publishedAt;
                    // public String updatedAt;
                }
            }

            // public boolean canReply;
            // public int totalReplyCount;
            // public boolean isPublic;
        }
    }
}

class ErrorJson {

    public ErrorBody error;

    class ErrorBody {
        //public int code;
        public String message;
    }
}

class VideoInfoList {
    
    //public String kind;
    //public String etag;
    public List<Item> items;
    //public PageInfo pageInfo;

    public class Item{
        //public String kind;
        //public String etag;
        public String id;
        public ContentDetails contentDetails;
        public class ContentDetails{
            public String duration;
            //public String dimension;
            ///public String definition;
            public boolean caption;
            //public boolean licensedContent;
            // "contentRating": {},
            //public String projection;
        }
        public Statistics statistics;
        public class Statistics{
            public int viewCount;
            public int likeCount;
            //public int favoriteCount;
            //public int commentCount;
        }
    }
    
    class PageInfo{
        public int totalResults;
        public int resultsPerPage;
    }
}

class GsonVDandHand {

    VD videoData = new VD();
    List<Hand> hands;

    GsonVDandHand(Pair<VideoData, List<Hand>> in) {
        var v = in.getFirst();
        videoData.likes = v.likes;
        videoData.views = v.views;
        videoData.title = v.title;
        videoData.thumbnail = v.thumbnail;
        videoData.releaseDate = v.releaseDate;
        videoData.videoLength = v.videoLength.toString();
        hands = in.getSecond();
    }

    Pair<VideoData, List<Hand>> get() {
        VideoData out = new VideoData(videoData.views, videoData.likes, videoData.title, videoData.thumbnail,
                videoData.releaseDate, Duration.parse(videoData.videoLength));
        return Pair.of(out, hands);

    }
    
    class VD {
        Integer views;
        Integer likes;
        String title;
        String thumbnail; 
        Date releaseDate;
        String videoLength;
        
    }
    
}