package com.scholaread.aicfg;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.CookieManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;

/** DSH-style multi-profile AI endpoint manager. Full-screen dialog over Settings. */
public class AiCfgPanel {

    static final String PREF = "scholaread_ai_cfg";
    static final String COOKIE_HOST = "https://com.scholaread.pdf2html";
    static final String COOKIE_NAME = "scholaread_local_ai_cfg";

    static final String DEF_BASE = "https://gleaming-vastly-baggie.ngrok-free.dev/strata/v1";
    static final String DEF_MODEL = "qwen3.8-flash-next_IQ3S_uncensored";
    static final String DEF_KEY = "m123456";
    static final String DEF_RELAY = "http://127.0.0.1:8787";

    static Dialog sOpen;
    static TextView sChatLabel;
    static View sChatThink, sChatSend;

    /** BFS for the "Thinking" toggle pill (text may be localized). */
    static TextView findThinking(ViewGroup g, int depth) {
        if (g == null || depth > 6) return null;
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (c instanceof TextView) {
                CharSequence s = ((TextView) c).getText();
                if (s != null) {
                    String t = s.toString().toLowerCase();
                    if (t.contains("thinking") || t.contains("\u601d\u8003")
                            || t.contains("\u63a8\u7406")) return (TextView) c;
                }
            }
            if (c instanceof ViewGroup) {
                TextView r = findThinking((ViewGroup) c, depth + 1);
                if (r != null) return r;
            }
        }
        return null;
    }

    /** Center the send button and chip on the Thinking pill's center line. */
    static void alignChatRow() {
        try {
            View t = sChatThink, sn = sChatSend, l = sChatLabel;
            if (t == null || sn == null || l == null) return;
            if (!t.isAttachedToWindow() || !sn.isAttachedToWindow() || !l.isAttachedToWindow()) return;
            int[] tp = new int[2], sp = new int[2], lp = new int[2];
            t.getLocationInWindow(tp);
            sn.getLocationInWindow(sp);
            l.getLocationInWindow(lp);
            float tc = tp[1] + t.getHeight() / 2f;
            float sd = tc - (sp[1] + sn.getHeight() / 2f);
            if (Math.abs(sd) > 1f) sn.setTranslationY(sd);
            l.setTranslationY(tc - (lp[1] + l.getHeight() / 2f));
            android.util.Log.e("AiCfgPanel", "align tTop=" + tp[1] + " tH=" + t.getHeight()
                    + " tc=" + tc + " snC=" + (sp[1] + sn.getHeight() / 2f)
                    + " lC=" + (lp[1] + l.getHeight() / 2f));
        } catch (Throwable ignore) {
        }
    }

    /** Adds a "🤖 model" chip left of the send button in the native AI chat input bar. */
    public static void attachChat(final View root) {
        attachChatTry(root, 0);
    }

    static void attachChatTry(final View root, final int attempt) {
        try {
            if (!(root instanceof ViewGroup)) return;
            final Context ctx = root.getContext();
            EditText et = findEditText((ViewGroup) root, 0);
            if (et == null) {
                if (attempt < 10) {
                    new android.os.Handler(android.os.Looper.getMainLooper())
                            .postDelayed(new Runnable() {
                                public void run() {
                                    attachChatTry(root, attempt + 1);
                                }
                            }, 600);
                }
                return;
            }
            if (sChatLabel != null && sChatLabel.isAttachedToWindow()) {
                refreshChatLabel();
                return;
            }
            Object pv = et.getParent();
            if (!(pv instanceof ViewGroup)) return;
            ViewGroup group = (ViewGroup) pv;
            View send = null, think = null;
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                View c = group.getChildAt(i);
                if (send == null && c instanceof ImageView) send = c;
                if (think == null && c instanceof TextView
                        && ((TextView) c).getCompoundDrawables()[0] != null) think = c;
            }
            if (send == null || send.getId() == 0) return;
            if (think == null) think = findThinking((ViewGroup) group, 0);
            TextView lbl = new TextView(ctx);
            lbl.setTextSize(12f);
            lbl.setTextColor(Color.parseColor("#1A56B8"));
            lbl.setSingleLine(true);
            lbl.setEllipsize(TextUtils.TruncateAt.END);
            lbl.setMaxWidth(dp(ctx, 150));
            lbl.setGravity(Gravity.CENTER_VERTICAL);
            lbl.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    showModelPicker(ctx);
                }
            });
            lbl.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View v) {
                    AiCfgPanel.show(ctx);
                    return true;
                }
            });
            boolean placed = false;
            try {
                Class<?> lpC = Class.forName("androidx.constraintlayout.widget.ConstraintLayout$LayoutParams");
                Object lp = lpC.getConstructor(int.class, int.class).newInstance(-2, -2);
                int anchor = (think != null && think.getId() != 0) ? think.getId() : send.getId();
                lpC.getField("rightToLeft").setInt(lp, send.getId());
                lpC.getField("topToTop").setInt(lp, anchor);
                lpC.getField("bottomToBottom").setInt(lp, anchor);
                if (think != null && think.getId() != 0) {
                    lpC.getField("leftToRight").setInt(lp, think.getId());
                    lpC.getField("horizontalBias").setFloat(lp, 1f);
                }
                lpC.getField("rightMargin").setInt(lp, dp(ctx, 6));
                group.addView(lbl, (ViewGroup.LayoutParams) lp);
                placed = true;
            } catch (Throwable t) {
                // not a ConstraintLayout / field renamed: skip chip instead of breaking input
            }
            if (!placed) return;
            sChatLabel = lbl;
            sChatThink = think;
            sChatSend = send;
            lbl.post(new Runnable() {
                public void run() {
                    alignChatRow();
                }
            });
            refreshChatLabel();
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                public void run() {
                    if (sChatLabel == null) return;
                    if (!sChatLabel.isAttachedToWindow()) {
                        sChatLabel = null;
                        return;
                    }
                    refreshChatLabel();
                    alignChatRow();
                    new android.os.Handler(android.os.Looper.getMainLooper())
                            .postDelayed(this, 1200);
                }
            }, 1200);
        } catch (Exception e) {
            // never break the chat UI
        }
    }

    /** One-tap profile switcher: single-choice list of all profiles. */
    static void showModelPicker(final Context ctx) {
        try {
            ensureSeed(ctx);
            final JSONArray arr = new JSONArray(prefs(ctx).getString("profiles", "[]"));
            if (arr.length() == 0) {
                AiCfgPanel.show(ctx);
                return;
            }
            String active = prefs(ctx).getString("active", "");
            String[] names = new String[arr.length()];
            int ci = -1;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject p = arr.getJSONObject(i);
                names[i] = p.optString("name", "?") + " \u00b7 " + p.optString("model", "");
                if (p.optString("name", "").equals(active)) ci = i;
            }
            new AlertDialog.Builder(ctx)
                    .setTitle("\u5207\u6362\u6a21\u578b")
                    .setSingleChoiceItems(names, ci, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int which) {
                            try {
                                activate(ctx, arr.getJSONObject(which).optString("name", ""));
                            } catch (Exception e) {
                            }
                            refreshChatLabel();
                            d.dismiss();
                        }
                    })
                    .setNegativeButton("\u7ba1\u7406\u63a5\u53e3\u2026", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int which) {
                            d.dismiss();
                            AiCfgPanel.show(ctx);
                        }
                    })
                    .show();
        } catch (Exception e) {
        }
    }

    static void refreshChatLabel() {
        try {
            TextView lbl = sChatLabel;
            if (lbl == null) return;
            Context c = lbl.getContext();
            String active = prefs(c).getString("active", "");
            String model = "";
            JSONArray arr = new JSONArray(prefs(c).getString("profiles", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject p = arr.getJSONObject(i);
                if (p.optString("name", "").equals(active)) {
                    model = p.optString("model", "");
                    break;
                }
            }
            if (model.length() > 22) model = model.substring(0, 20) + "..";
            lbl.setText("\uD83E\uDD16 " + (model.isEmpty() ? "\u9009\u6a21\u578b" : model));
        } catch (Exception e) {
        }
    }

    static EditText findEditText(ViewGroup g, int depth) {
        if (depth > 12) return null;
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (c instanceof EditText) return (EditText) c;
            if (c instanceof ViewGroup) {
                EditText r = findEditText((ViewGroup) c, depth + 1);
                if (r != null) return r;
            }
        }
        return null;
    }

    public static void show(final Context ctx) {
        if (sOpen != null && sOpen.isShowing()) return;
        ensureSeed(ctx);

        Dialog d = new Dialog(ctx);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        TextView bar = new TextView(ctx);
        bar.setText("\u2190  \u81ea\u5b9a\u4e49 AI \u63a5\u53e3");
        bar.setTextSize(19f);
        bar.setTextColor(Color.parseColor("#1A56B8"));
        bar.setTypeface(bar.getTypeface(), android.graphics.Typeface.BOLD);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(ctx, 18), dp(ctx, 16), dp(ctx, 18), dp(ctx, 16));
        bar.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (sOpen != null) sOpen.dismiss();
            }
        });
        root.addView(bar, lp(-1, -2));

        View div = new View(ctx);
        div.setBackgroundColor(Color.parseColor("#E5E5E5"));
        root.addView(div, lp(-1, 2));

        final ListView list = new ListView(ctx);
        root.addView(list, lp(-1, 0, 1f));

        Button add = new Button(ctx);
        add.setText("+  \u6dfb\u52a0\u63a5\u53e3");
        add.setTextSize(16f);
        add.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                editDialog(ctx, list, -1);
            }
        });
        LinearLayout.LayoutParams alp = lp(-1, -2);
        alp.setMargins(dp(ctx, 16), dp(ctx, 8), dp(ctx, 16), dp(ctx, 16));
        root.addView(add, alp);

        final BaseAdapter ad = new BaseAdapter() {
            public int getCount() {
                try {
                    return new JSONArray(prefs(ctx).getString("profiles", "[]")).length();
                } catch (Exception e) {
                    return 0;
                }
            }

            public Object getItem(int i) {
                try {
                    return new JSONArray(prefs(ctx).getString("profiles", "[]")).getJSONObject(i);
                } catch (Exception e) {
                    return null;
                }
            }

            public long getItemId(int i) {
                return i;
            }

            public View getView(int i, View cv, ViewGroup vg) {
                final Context c = ctx;
                final JSONObject p;
                final String active;
                try {
                    p = new JSONArray(prefs(c).getString("profiles", "[]")).getJSONObject(i);
                    active = prefs(c).getString("active", "");
                } catch (Exception e) {
                    return new View(c);
                }
                boolean isActive = p.optString("name", "").equals(active);

                LinearLayout row = new LinearLayout(c);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(c, 18), dp(c, 14), dp(c, 14), dp(c, 14));

                TextView name = new TextView(c);
                name.setText(p.optString("name", "?"));
                name.setTextSize(16f);
                name.setTextColor(Color.parseColor("#222222"));
                row.addView(name, lp(-2, -2, 1f));

                if (isActive) {
                    TextView badge = new TextView(c);
                    badge.setText("\u4f7f\u7528\u4e2d");
                    badge.setTextSize(12f);
                    badge.setTextColor(Color.parseColor("#178A4C"));
                    badge.setBackgroundColor(Color.parseColor("#E4F5EA"));
                    badge.setPadding(dp(c, 8), dp(c, 3), dp(c, 8), dp(c, 3));
                    row.addView(badge, lp(-2, -2));
                }

                Button edit = new Button(c);
                edit.setText("\u7f16\u8f91");
                edit.setTextSize(13f);
                edit.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        editDialog(c, list, i);
                    }
                });
                row.addView(edit, lp(-2, -2));

                Button del = new Button(c);
                del.setText("\u5220\u9664");
                del.setTextSize(13f);
                del.setTextColor(Color.parseColor("#CC3333"));
                del.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        deleteProfile(c, list, i);
                    }
                });
                row.addView(del, lp(-2, -2));

                row.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        activate(c, p.optString("name", ""));
                        Toast.makeText(c, "\u5df2\u5207\u6362\u5230\uff1a" + p.optString("name", ""), Toast.LENGTH_SHORT).show();
                        refresh(c, list);
                    }
                });
                return row;
            }
        };
        list.setAdapter(ad);
        d.setContentView(root);
        d.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.WHITE));
        sOpen = d;
        d.show();
    }

    // adapter refresh helper (re-set same adapter to force reload)
    static void refresh(Context c, ListView list) {
        list.setAdapter(list.getAdapter());
    }

    static void activate(Context ctx, String name) {
        try {
            JSONArray arr = new JSONArray(prefs(ctx).getString("profiles", "[]"));
            String active = "";
            for (int i = 0; i < arr.length(); i++) {
                JSONObject p = arr.getJSONObject(i);
                if (p.optString("name", "").equals(name)) {
                    active = name;
                    writeCookie(p);
                    break;
                }
            }
            prefs(ctx).edit().putString("active", active).apply();
        } catch (Exception e) {
            toast(ctx, "\u5207\u6362\u5931\u8d25: " + e);
        }
    }

    static void deleteProfile(final Context ctx, final ListView list, final int idx) {
        try {
            final JSONArray arr = new JSONArray(prefs(ctx).getString("profiles", "[]"));
            final String gone = arr.getJSONObject(idx).optString("name", "");
            new android.app.AlertDialog.Builder(ctx)
                    .setTitle("\u5220\u9664\u63a5\u53e3")
                    .setMessage("\u786e\u5b9a\u5220\u9664\u201c" + gone + "\u201d\uff1f")
                    .setPositiveButton("\u5220\u9664", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int w) {
                            try {
                                arr.remove(idx);
                                prefs(ctx).edit().putString("profiles", arr.toString()).apply();
                                String active = prefs(ctx).getString("active", "");
                                if (active.equals(gone)) {
                                    if (arr.length() > 0) {
                                        JSONObject first = arr.getJSONObject(0);
                                        activate(ctx, first.optString("name", ""));
                                    } else {
                                        prefs(ctx).edit().putString("active", "").apply();
                                    }
                                }
                                refresh(ctx, list);
                            } catch (Exception e) {
                                toast(ctx, "\u5220\u9664\u5931\u8d25: " + e);
                            }
                        }
                    })
                    .setNegativeButton("\u53d6\u6d88", null)
                    .show();
        } catch (Exception e) {
            toast(ctx, "\u5220\u9664\u5931\u8d25: " + e);
        }
    }

    static void editDialog(final Context ctx, final ListView list, final int idx) {
        try {
            JSONObject p = null;
            JSONArray arr = new JSONArray(prefs(ctx).getString("profiles", "[]"));
            if (idx >= 0 && idx < arr.length()) p = arr.getJSONObject(idx);

            // for a NEW profile, prefill base/model/key/relay from the active one
            JSONObject seed = p;
            if (seed == null) {
                String act = prefs(ctx).getString("active", "");
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject q = arr.getJSONObject(i);
                    if (q.optString("name", "").equals(act)) {
                        seed = q;
                        break;
                    }
                }
                if (seed == null && arr.length() > 0) seed = arr.getJSONObject(0);
            }

            final EditText fName = field(ctx, "\u540d\u79f0", p == null ? "" : p.optString("name", ""), InputType.TYPE_CLASS_TEXT);
            final EditText fBase = field(ctx, "API \u5730\u5740\uff08\u4ee5 /v1 \u7ed3\u5c3e\uff0c\u81ea\u52a8\u7ea0\u6b63\uff09", seed == null ? "" : seed.optString("baseUrl", ""), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
            final EditText fModel = field(ctx, "\u6a21\u578b\u540d", seed == null ? "" : seed.optString("model", ""), InputType.TYPE_CLASS_TEXT);
            final EditText fKey = field(ctx, "API Key", seed == null ? "" : seed.optString("apiKey", ""), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            final EditText fRelay = field(ctx, "\u4e2d\u8f6c\u5730\u5740\uff08\u7559\u7a7a=\u76f4\u8fde\uff09", seed == null ? DEF_RELAY : seed.optString("relay", DEF_RELAY), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);

            LinearLayout box = new LinearLayout(ctx);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(dp(ctx, 24), dp(ctx, 12), dp(ctx, 24), 0);
            box.addView((View) fName.getTag());
            box.addView((View) fBase.getTag());

            Button fetch = new Button(ctx);
            fetch.setText("\u83b7\u53d6\u6a21\u578b\u5217\u8868\uff08\u540c\u65f6\u6d4b\u8bd5\u8fde\u63a5\uff09");
            fetch.setTextSize(14f);
            fetch.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    String base = normalize(fBase.getText().toString().trim());
                    if (base.isEmpty()) {
                        toast(ctx, "\u5148\u586b\u5199 API \u5730\u5740");
                        return;
                    }
                    toast(ctx, "\u6b63\u5728\u83b7\u53d6\u6a21\u578b\u5217\u8868...");
                    fetchModels(ctx, base, fKey.getText().toString().trim(),
                            fRelay.getText().toString().trim().replaceAll("/+$", ""), fModel);
                }
            });
            box.addView(fetch, lp(-1, -2));

            box.addView((View) fModel.getTag());
            box.addView((View) fKey.getTag());
            box.addView((View) fRelay.getTag());

            ScrollView sv = new ScrollView(ctx);
            sv.addView(box);

            final JSONArray arrF = arr;
            final int idxF = idx;

            LinearLayout root = new LinearLayout(ctx);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackgroundColor(Color.WHITE);

            TextView ttl = new TextView(ctx);
            ttl.setText(idxF < 0 ? "\u6dfb\u52a0\u63a5\u53e3" : "\u7f16\u8f91\u63a5\u53e3");
            ttl.setTextSize(19f);
            ttl.setTextColor(Color.parseColor("#1A56B8"));
            ttl.setTypeface(ttl.getTypeface(), android.graphics.Typeface.BOLD);
            ttl.setGravity(Gravity.CENTER_VERTICAL);
            ttl.setPadding(dp(ctx, 24), dp(ctx, 16), dp(ctx, 24), dp(ctx, 12));
            root.addView(ttl, lp(-1, -2));

            View dv = new View(ctx);
            dv.setBackgroundColor(Color.parseColor("#E5E5E5"));
            root.addView(dv, lp(-1, 2));

            root.addView(sv, lp(-1, 0, 1f));

            View dv2 = new View(ctx);
            dv2.setBackgroundColor(Color.parseColor("#E5E5E5"));
            root.addView(dv2, lp(-1, 2));

            LinearLayout bar = new LinearLayout(ctx);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            Button cancel = new Button(ctx);
            cancel.setText("\u53d6\u6d88");
            cancel.setTextSize(16f);
            Button save = new Button(ctx);
            save.setText("\u4fdd\u5b58");
            save.setTextSize(16f);
            save.setTextColor(Color.parseColor("#1A56B8"));
            bar.addView(cancel, lp(-2, -2, 1f));
            bar.addView(save, lp(-2, -2, 1f));
            root.addView(bar, lp(-1, -2));

            final Dialog ed = new Dialog(ctx);
            ed.requestWindowFeature(Window.FEATURE_NO_TITLE);
            ed.setContentView(root);
            ed.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            ed.getWindow().setBackgroundDrawable(new ColorDrawable(Color.WHITE));
            ed.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

            cancel.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    ed.dismiss();
                }
            });
            save.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    try {
                        String name = fName.getText().toString().trim();
                        if (name.isEmpty()) {
                            toast(ctx, "\u8bf7\u586b\u5199\u540d\u79f0");
                            return;
                        }
                        for (int i = 0; i < arrF.length(); i++) {
                            if (i != idxF && arrF.getJSONObject(i).optString("name", "").equals(name)) {
                                toast(ctx, "\u540d\u79f0\u5df2\u5b58\u5728");
                                return;
                            }
                        }
                        JSONObject np = new JSONObject();
                        np.put("name", name);
                        np.put("baseUrl", normalize(fBase.getText().toString().trim()));
                        np.put("model", fModel.getText().toString().trim());
                        np.put("apiKey", fKey.getText().toString().trim());
                        np.put("relay", fRelay.getText().toString().trim().replaceAll("/+$", ""));
                        String oldName = idxF >= 0 ? arrF.getJSONObject(idxF).optString("name", "") : "";
                        if (idxF >= 0) {
                            arrF.remove(idxF);
                            int at = idxF < arrF.length() ? idxF : arrF.length();
                            arrF.put(at, np);
                        } else {
                            arrF.put(np);
                        }
                        prefs(ctx).edit().putString("profiles", arrF.toString()).apply();
                        String active = prefs(ctx).getString("active", "");
                        if (active.equals(oldName) || active.isEmpty() || idxF < 0 && arrF.length() == 1) {
                            activate(ctx, name);
                        }
                        refresh(ctx, list);
                        toast(ctx, "\u5df2\u4fdd\u5b58\uff0c\u7ffb\u8bd1\u7acb\u5373\u751f\u6548");
                        ed.dismiss();
                    } catch (Exception e) {
                        toast(ctx, "\u4fdd\u5b58\u5931\u8d25: " + e);
                    }
                }
            });
            ed.show();
        } catch (Exception e) {
            toast(ctx, "\u6253\u5f00\u5931\u8d25: " + e);
        }
    }

    static void ensureSeed(Context ctx) {
        try {
            SharedPreferences sp = prefs(ctx);
            if (sp.getString("profiles", "").isEmpty()) {
                JSONArray arr = new JSONArray();
                JSONObject p = new JSONObject();
                p.put("name", "\u9ed8\u8ba4");
                p.put("baseUrl", DEF_BASE);
                p.put("model", DEF_MODEL);
                p.put("apiKey", DEF_KEY);
                p.put("relay", DEF_RELAY);
                arr.put(p);
                sp.edit().putString("profiles", arr.toString()).putString("active", "\u9ed8\u8ba4").apply();
            }
        } catch (Exception e) {
            // ignore
        }
    }

    static void fetchModels(final Context ctx, final String base, final String key,
                            final String relay, final EditText fModel) {
        new Thread(new Runnable() {
            public void run() {
                String err = null;
                String[] ids = null;
                String url = null;
                try {
                    String target = base + "/models";
                    url = relay.isEmpty() ? target
                            : relay + "/relay/" + URLEncoder.encode(target, "UTF-8");
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection)
                            new java.net.URL(url).openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(15000);
                    c.setRequestMethod("GET");
                    c.setRequestProperty("Accept", "application/json");
                    c.setRequestProperty("User-Agent", "Mozilla/5.0 (AiCfgPanel)");
                    c.setRequestProperty("ngrok-skip-browser-warning", "true");
                    if (!key.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + key);
                    int code = c.getResponseCode();
                    java.io.InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                    StringBuilder sb = new StringBuilder();
                    if (in != null) {
                        java.io.BufferedReader r = new java.io.BufferedReader(
                                new java.io.InputStreamReader(in, "UTF-8"));
                        String line;
                        while ((line = r.readLine()) != null) sb.append(line);
                        r.close();
                    }
                    if (code != 200) {
                        String body = sb.toString();
                        err = "HTTP " + code + ": " + body.substring(0, Math.min(120, body.length()));
                        android.util.Log.e("AiCfgPanel", "fetch HTTP " + code + " url=" + url
                                + " body=" + body.substring(0, Math.min(300, body.length())));
                    } else {
                        JSONArray data = new JSONObject(sb.toString()).optJSONArray("data");
                        if (data == null) {
                            String body = sb.toString();
                            err = "\u54cd\u5e94\u6ca1\u6709 data \u5b57\u6bb5: "
                                    + body.substring(0, Math.min(100, body.length()));
                            android.util.Log.e("AiCfgPanel", "no data field url=" + url
                                    + " body=" + body.substring(0, Math.min(300, body.length())));
                        } else {
                            java.util.ArrayList<String> list = new java.util.ArrayList<String>();
                            for (int i = 0; i < data.length(); i++) {
                                String id = data.getJSONObject(i).optString("id", "");
                                if (!id.isEmpty()) list.add(id);
                            }
                            if (list.isEmpty()) {
                                err = "\u6a21\u578b\u5217\u8868\u4e3a\u7a7a";
                            } else {
                                ids = list.toArray(new String[0]);
                            }
                        }
                    }
                } catch (Exception e) {
                    err = e.toString();
                    android.util.Log.e("AiCfgPanel", "fetch exception url=" + url, e);
                }
                final String[] fIds = ids;
                final String fErr = err;
                new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                    public void run() {
                        if (fErr != null) {
                            toast(ctx, "\u8fde\u63a5\u5931\u8d25: " + fErr);
                            return;
                        }
                        if (fIds.length == 1) {
                            fModel.setText(fIds[0]);
                            toast(ctx, "\u8fde\u63a5\u6210\u529f\uff0c\u5df2\u81ea\u52a8\u586b\u5165\u6a21\u578b: " + fIds[0]);
                            return;
                        }
                        fModel.setText(fIds[0]);
                        toast(ctx, "\u8fde\u63a5\u6210\u529f\uff0c\u5171 " + fIds.length + " \u4e2a\u6a21\u578b\uff0c\u70b9\u51fb\u53ef\u5207\u6362");
                        new android.app.AlertDialog.Builder(ctx)
                                .setTitle("\u9009\u62e9\u6a21\u578b")
                                .setItems(fIds, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface d, int w) {
                                        fModel.setText(fIds[w]);
                                    }
                                })
                                .show();
                    }
                });
            }
        }).start();
    }

    static void writeCookie(JSONObject p) throws Exception {
        JSONObject o = new JSONObject();
        o.put("baseUrl", p.optString("baseUrl", ""));
        o.put("model", p.optString("model", ""));
        o.put("apiKey", p.optString("apiKey", ""));
        o.put("relay", p.optString("relay", ""));
        String enc = URLEncoder.encode(o.toString(), "UTF-8");
        CookieManager cm = CookieManager.getInstance();
        cm.setCookie(COOKIE_HOST, COOKIE_NAME + "=" + enc + "; path=/; max-age=31536000");
        cm.flush();
    }

    static String normalize(String u) {
        u = u.replaceAll("/+$", "");
        u = u.replaceAll("/chat/completions$", "");
        while (u.matches(".*/v\\d+/v\\d+$")) {
            u = u.replaceFirst("/v\\d+$", "");
        }
        if (!u.matches(".*/v\\d+$")) u = u + "/v1";
        return u;
    }

    static EditText field(Context c, String label, String value, int inputType) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(c, 6), 0, dp(c, 6));
        TextView tv = new TextView(c);
        tv.setText(label);
        tv.setTextSize(13f);
        tv.setTextColor(Color.parseColor("#666666"));
        box.addView(tv, lp(-2, -2));
        EditText et = new EditText(c);
        et.setText(value);
        et.setInputType(inputType);
        et.setTextSize(15f);
        box.addView(et, lp(-1, -2));
        return box2(c, box, et);
    }

    // returns the EditText but keeps label wrapper via tag
    static EditText box2(Context c, LinearLayout box, EditText et) {
        et.setTag(box);
        return et;
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    static void toast(Context c, String m) {
        Toast.makeText(c, m, Toast.LENGTH_SHORT).show();
    }

    static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density);
    }

    static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    static LinearLayout.LayoutParams lp(int w, int h, float weight) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.weight = weight;
        return p;
    }

    /** Diagnostic: log a parsed AIChatResponse via reflection. */
    public static void logResp(Object o) {
        try {
            Object fr = o.getClass().getField("finish_reason").get(o);
            Object ct = o.getClass().getField("content").get(o);
            android.util.Log.e("AiCfgPanel", "resp fr=" + fr + " contentLen="
                    + (ct == null ? "null" : String.valueOf(ct).length()));
        } catch (Throwable t) {
            android.util.Log.e("AiCfgPanel", "logResp fail " + t);
        }
    }

    /** Diagnostic: log an SSE line that failed the data: prefix check. */
    public static void logLine(Object o) {
        try {
            String s = String.valueOf(o);
            android.util.Log.e("AiCfgPanel", "skip line: " + s.substring(0, Math.min(80, s.length())));
        } catch (Throwable t) {
        }
    }

    /** JS bridge: profile list as [{name,model},...] JSON for the research page. */
    public static String jsProfiles(Context ctx) {
        try {
            ensureSeed(ctx);
            JSONArray arr = new JSONArray(prefs(ctx).getString("profiles", "[]"));
            JSONArray out = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject p = arr.getJSONObject(i);
                JSONObject o = new JSONObject();
                o.put("name", p.optString("name", ""));
                o.put("model", p.optString("model", ""));
                out.put(o);
            }
            return out.toString();
        } catch (Exception e) {
            return "[]";
        }
    }

    /** JS bridge: active profile name. */
    public static String jsActive(Context ctx) {
        try {
            return prefs(ctx).getString("active", "");
        } catch (Exception e) {
            return "";
        }
    }
}
