package infra.fingerprint;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generic fingerprinting utility: given a collection of beans, a list of key-field names and
 * an ID-field name, it will group objects by key-values, count total hits per group, and
 * collect a sample of up to sampleLimit IDs per group.
 *
 * @param <T>  type of your POJO
 * @param <ID> type of the identifier field
 */
public class Fingerprinter<T, ID> {

    private final List<String> keyFields;
    private final String idField;
    private final int sampleLimit;

    private final Method idGetter;
    private final List<Method> keyGetters;

    private final Map<GroupKey, GroupStats<ID>> stats = new HashMap<>();

    /**
     * @param clazz       your POJO class
     * @param keyFields   list of field names to group by (must have standard getters)
     * @param idField     the field name whose values you want to sample
     * @param sampleLimit maximum number of IDs to collect per group
     */
    public Fingerprinter(Class<T> clazz, List<String> keyFields, String idField, int sampleLimit) {
        this.keyFields = keyFields;
        this.idField    = idField;
        this.sampleLimit = sampleLimit;

        // locate and cache Method objects for reflection
        this.keyGetters = new ArrayList<>(keyFields.size());
        for (String f : keyFields) {
            this.keyGetters.add(findGetter(clazz, f));
        }
        this.idGetter = findGetter(clazz, idField);
    }

    /**
     * Process all items in the collection.
     */
    public void process(Collection<T> items) {
        for (T item : items) {
            // build key
            Map<String,Object> kv = new LinkedHashMap<>();
            for (int i = 0; i < keyFields.size(); i++) {
                Object val = invoke(keyGetters.get(i), item);
                kv.put(keyFields.get(i), val);
            }
            GroupKey groupKey = new GroupKey(kv);

            // update stats
            GroupStats<ID> g = stats.computeIfAbsent(groupKey, k -> new GroupStats<>(0L, new ArrayList<>()));

            // increment total count
            g.count++;

            // collect ID if under sample cap
            @SuppressWarnings("unchecked")
            ID idVal = (ID) invoke(idGetter, item);
            if (g.sampleIds.size() < sampleLimit) {
                g.sampleIds.add(idVal);
            }
        }
    }

    /** @return an unmodifiable view of the grouping results */
    public Map<GroupKey, GroupStats<ID>> getResults() {
        return Collections.unmodifiableMap(stats);
    }

    // ------------------------------------------------------------------------
    // Reflection helpers
    // ------------------------------------------------------------------------
    private Method findGetter(Class<T> clazz, String fieldName) {
        String cap = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        String getterName = "get" + cap;
        try {
            Method m = clazz.getMethod(getterName);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("No getter for field '" + fieldName + "' in " + clazz, e);
        }
    }

    private Object invoke(Method m, T obj) {
        try {
            return m.invoke(obj);
        } catch (Exception e) {
            throw new RuntimeException("Error invoking " + m, e);
        }
    }

    // ------------------------------------------------------------------------
    // Data‐structure for a composite group key
    // ------------------------------------------------------------------------
    public static class GroupKey {
        private final Map<String,Object> fieldValues;  // preserves insertion order

        public GroupKey(Map<String,Object> fieldValues) {
            this.fieldValues = new LinkedHashMap<>(fieldValues);
        }

        public Map<String,Object> getFieldValues() {
            return Collections.unmodifiableMap(fieldValues);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof GroupKey)) return false;
            GroupKey other = (GroupKey) o;
            return Objects.equals(fieldValues, other.fieldValues);
        }

        @Override
        public int hashCode() {
            return Objects.hash(fieldValues);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            fieldValues.forEach((k,v) -> sb.append(k).append('=').append(v).append(';'));
            return sb.toString();
        }
    }

    // ------------------------------------------------------------------------
    // Per‐group statistics: total count + sampled IDs
    // ------------------------------------------------------------------------
    public static class GroupStats<ID> {
        private long count;
        private final List<ID> sampleIds;

        private GroupStats(long count, List<ID> sampleIds) {
            this.count      = count;
            this.sampleIds  = sampleIds;
        }

        /** total number of items in this group */
        public long getCount() {
            return count;
        }

        /** up to sampleLimit identifiers from this group (may include duplicates) */
        public List<ID> getSampleIds() {
            return Collections.unmodifiableList(sampleIds);
        }
    }
    public List<Map.Entry<GroupKey,GroupStats<ID>>> topGroups(int n) {
        return stats.entrySet().stream()
                .sorted((e1,e2) -> Long.compare(e2.getValue().getCount(), e1.getValue().getCount()))
                .limit(n)
                .collect(Collectors.toList());
    }

}
