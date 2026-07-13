package ch.threema.domain.protocol.csp.messages.poll;

import ch.threema.domain.protocol.csp.messages.BadMessageException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public class PollDataChoice {
    private final static String KEY_CHOICES_ID = "i";
    private final static String KEY_CHOICES_NAME = "n";
    private final static String KEY_CHOICES_ORDER = "o";
    private final static String KEY_RESULT = "r";
    private final static String KEY_TOTAL_VOTES = "t";

    int id;
    String name;
    int order;
    final int[] pollDataChoiceResults;
    int totalVotes;

    public PollDataChoice(int resultSize) {
        this.pollDataChoiceResults = new int[resultSize];
    }

    public int getId() {
        return id;
    }

    /**
     * @deprecated use the {@link PollDataChoiceBuilder instead}
     */
    @Deprecated
    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    /**
     * @deprecated use the {@link PollDataChoiceBuilder instead}
     */
    @Deprecated
    public void setName(String name) {
        this.name = name;
    }

    public int getOrder() {
        return order;
    }

    /**
     * @deprecated use the {@link PollDataChoiceBuilder instead}
     */
    @Deprecated
    public void setOrder(int order) {
        this.order = order;
    }

    /**
     * @deprecated use the {@link PollDataChoiceBuilder instead}
     */
    @Deprecated
    public PollDataChoice addResult(int pos, int value) {
        if (pos >= 0 && pos < this.pollDataChoiceResults.length) {
            this.pollDataChoiceResults[pos] = value;
        }

        return this;
    }

    public Integer getResult(int pos) {
        if (pos >= 0 && pos < this.pollDataChoiceResults.length) {
            return this.pollDataChoiceResults[pos];
        }
        return null;
    }

    public int getTotalVotes() {
        return this.totalVotes;
    }

    public void setTotalVotes(int totalVotes) {
        this.totalVotes = totalVotes;
    }

    public static PollDataChoice parse(String jsonObjectString) throws BadMessageException {
        try {
            JSONObject o = new JSONObject(jsonObjectString);
            return parse(o);
        } catch (JSONException e) {
            throw new BadMessageException("TM033 invalid JSON (" + e.getMessage() + ")");
        }
    }

    public static PollDataChoice parse(JSONObject o) throws BadMessageException {
        try {
            if (o == null) {
                throw new BadMessageException("TM033");
            }

            final JSONArray resultArray;
            if (o.has(KEY_RESULT)) {
                resultArray = o.getJSONArray(KEY_RESULT);
            } else {
                resultArray = null;
            }

            PollDataChoice pollDataChoice = new PollDataChoice(resultArray != null ? resultArray.length() : 0);
            pollDataChoice.setId(o.getInt(KEY_CHOICES_ID));
            pollDataChoice.setName(o.getString(KEY_CHOICES_NAME));
            pollDataChoice.setOrder(o.getInt(KEY_CHOICES_ORDER));

            if (o.has(KEY_TOTAL_VOTES)) {
                pollDataChoice.setTotalVotes(o.getInt(KEY_TOTAL_VOTES));
            }

            if (resultArray != null) {
                for (int n = 0; n < resultArray.length(); n++) {
                    pollDataChoice.addResult(n, resultArray.getInt(n));
                }
            }

            return pollDataChoice;
        } catch (JSONException e) {
            throw new BadMessageException("TM033");
        }
    }

    public JSONObject getJsonObject() throws BadMessageException {
        JSONObject o = new JSONObject();
        try {
            o.put(KEY_CHOICES_ID, this.getId());
            o.put(KEY_CHOICES_NAME, this.getName());
            o.put(KEY_CHOICES_ORDER, this.getOrder());

            JSONArray resultArray = new JSONArray();
            for (Integer r : this.pollDataChoiceResults) {
                resultArray.put(r);
            }
            o.put(KEY_RESULT, resultArray);
            o.put(KEY_TOTAL_VOTES, this.getTotalVotes());
        } catch (Exception e) {
            throw new BadMessageException("TM033");
        }
        return o;
    }

    public void write(ByteArrayOutputStream bos) throws Exception {
        bos.write(this.generateString().getBytes(StandardCharsets.US_ASCII));
    }

    public String generateString() throws BadMessageException {
        return this.getJsonObject().toString();
    }
}
