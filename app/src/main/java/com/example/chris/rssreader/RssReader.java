package com.example.chris.rssreader;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TwoLineListItem;
import android.util.Xml;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;



public class RssReader extends ListActivity {

    public static final int LENGTH = 100;
    public static final String STRING_KEY = "strings";
    public static final String SELECTION_KEY = "selection";
    public static final String URL_KEY = "url";
    public static final String STATUS_KEY = "status";
    private RSSListAdapter adapter;
    private EditText urlText;
    private TextView statusText;
    private Handler handler;
    private RSSWorker worker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.rss_layout);

        List<RssItem> items = new ArrayList<RssItem>();
        adapter = new RSSListAdapter(this, items);
        getListView().setAdapter(adapter);
        urlText = findViewById(R.id.urltext);
        statusText = findViewById(R.id.statustext);
        Button button = findViewById(R.id.download);
        button.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                doRSS(urlText.getText());
            }
        });
        handler = new Handler();
    }

    private class RSSListAdapter extends ArrayAdapter<RssItem> {
        private LayoutInflater inflater;
        public RSSListAdapter(Context context, List<RssItem> objects) {
            super(context, 0, objects);
            inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            TwoLineListItem view;
            if (convertView == null) {
                view = (TwoLineListItem) inflater.inflate(android.R.layout.simple_list_item_2, null);
            } else {
                view = (TwoLineListItem) convertView;
            }
            RssItem item = this.getItem(position);
            assert item != null;
            view.getText1().setText(item.getTitle());
            String descr = item.getDescription().toString();
            descr = removeTags(descr);
            view.getText2().setText(descr.substring(0, Math.min(descr.length(), LENGTH)));
            return view;
        }
    }

    public String removeTags(String str) {
        str = str.replaceAll("<.*?>", " ");
        str = str.replaceAll("\\s+", " ");
        return str;
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        RssItem item = adapter.getItem(position);
        assert item != null;
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(item.getLink().toString()));
        startActivity(intent);
    }

    public void resetUI() {
        List<RssItem> items = new ArrayList<RssItem>();
        adapter = new RSSListAdapter(this, items);
        getListView().setAdapter(adapter);
        statusText.setText("");
        urlText.requestFocus();
    }

    public synchronized void setCurrentWorker(RSSWorker worker) {
        if (this.worker != null) this.worker.interrupt();
        this.worker = worker;
    }

    public synchronized boolean isCurrentWorker(RSSWorker worker) {
        return (this.worker == worker);
    }

    private void doRSS(CharSequence rssUrl) {
        RSSWorker rssWorker = new RSSWorker(rssUrl);
        setCurrentWorker(rssWorker);
        resetUI();
        statusText.setText("Downloading\u2026");
        rssWorker.start();
    }

    private class AddItem implements Runnable {
        RssItem mItem;
        AddItem(RssItem item) {
            mItem = item;
        }
        public void run() {
            adapter.add(mItem);
        }

    }

    private class RSSWorker extends Thread {
        private CharSequence mUrl;
        public RSSWorker(CharSequence url) {
            mUrl = url;
        }
        @Override
        public void run() {
            String status = "";
            try {
                URL url = new URL(mUrl.toString());
                URLConnection connection = url.openConnection();
                connection.setConnectTimeout(10000);
                connection.connect();
                InputStream in = connection.getInputStream();
                parseRSS(in, adapter);
                status = "done";
            } catch (Exception e) {
                status = "failed:" + e.getMessage();
            }
            final String temp = status;
            if (isCurrentWorker(this)) {
                handler.post(new Runnable() {
                    public void run() {
                        statusText.setText(temp);
                    }
                });
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, 0, 0, "Slashdot")
                .setOnMenuItemClickListener(new RSSMenu("http://rss.slashdot.org/Slashdot/slashdot"));
        menu.add(0, 0, 0, "Google News")
                .setOnMenuItemClickListener(new RSSMenu("http://news.google.com/?output=rss"));

        menu.add(0, 0, 0, "News.com")
                .setOnMenuItemClickListener(new RSSMenu("http://news.com.com/2547-1_3-0-20.xml"));
        menu.add(0, 0, 0, "Bad Url")
                .setOnMenuItemClickListener(new RSSMenu("http://nifty.stanford.edu:8080"));
        menu.add(0, 0, 0, "Reset")
                .setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                    public boolean onMenuItemClick(MenuItem item) {
                        resetUI();
                        return true;
                    }
                });
        return true;
    }

    private class RSSMenu implements MenuItem.OnMenuItemClickListener {
        private CharSequence mUrl;
        RSSMenu(CharSequence url) {
            mUrl = url;
        }
        public boolean onMenuItemClick(MenuItem item) {
            urlText.setText(mUrl);
            urlText.requestFocus();
            return true;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        int count = adapter.getCount();
        ArrayList<CharSequence> strings = new ArrayList<CharSequence>();
        for (int i = 0; i < count; i++) {
            RssItem item = adapter.getItem(i);
            assert item != null;
            strings.add(item.getTitle());
            strings.add(item.getLink());
            strings.add(item.getDescription());
        }
        outState.putSerializable(STRING_KEY, strings);
        if (getListView().hasFocus()) {
            outState.putInt(SELECTION_KEY, getListView().getSelectedItemPosition());
        }
        outState.putString(URL_KEY, urlText.getText().toString());
        outState.putCharSequence(STATUS_KEY, statusText.getText());
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        if (state == null) return;
        List<CharSequence> strings = (ArrayList<CharSequence>)state.getSerializable(STRING_KEY);
        List<RssItem> items = new ArrayList<RssItem>();
        assert strings != null;
        for (int i = 0; i < strings.size(); i += 3) {
            items.add(new RssItem(strings.get(i), strings.get(i + 1), strings.get(i + 2)));
        }
        adapter = new RSSListAdapter(this, items);
        getListView().setAdapter(adapter);
        if (state.containsKey(SELECTION_KEY)) {
            getListView().requestFocus(View.FOCUS_FORWARD);
            getListView().setSelection(state.getInt(SELECTION_KEY));
        }
        urlText.setText(state.getCharSequence(URL_KEY));
        statusText.setText(state.getCharSequence(STATUS_KEY));
    }


    void parseRSS(InputStream in, RSSListAdapter adapter) throws IOException, XmlPullParserException {
        XmlPullParser newPullParser = Xml.newPullParser();
        newPullParser.setInput(in, null);
        int eventType;
        String title = "";
        String link = "";
        String description = "";
        eventType = newPullParser.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                String tag = newPullParser.getName();
                if (tag.equals("item")) {
                    title = link = description = "";
                } else if (tag.equals("title")) {
                    newPullParser.next();
                    title = newPullParser.getText();
                } else if (tag.equals("link")) {
                    newPullParser.next();
                    link = newPullParser.getText();
                } else if (tag.equals("description")) {
                    newPullParser.next();
                    description = newPullParser.getText();
                }
            } else if (eventType == XmlPullParser.END_TAG) {
                String tag = newPullParser.getName();
                if (tag.equals("item")) {
                    RssItem item = new RssItem(title, link, description);
                    handler.post(new AddItem(item));
                }
            }
            eventType = newPullParser.next();
        }
    }
}