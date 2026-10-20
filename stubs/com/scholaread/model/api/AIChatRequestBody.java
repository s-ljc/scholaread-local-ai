package com.scholaread.model.api;

import java.util.List;

public class AIChatRequestBody {
    public String type;
    public String reading_id;
    public String content;
    public QAConfig qa_config;

    public static class QAConfig {
        public List share_ids;
        public String target;
        public String context_type;
        public List contexts;
        public String ref_msg_id;
        public String source;
        public boolean is_reasoning;
    }
}
