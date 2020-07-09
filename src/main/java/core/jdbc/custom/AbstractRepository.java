package core.jdbc.custom;

import core.jdbc.ConnectionManager;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class AbstractRepository<T, V> implements Repository<T, V> {

    private T t;

    AbstractRepository(T t) {
        this.t = t;
    }

    private Connection getConnection() {
        return ConnectionManager.getConnection();
    }

    private PreparedStatement getPreparedStatement(String sql) throws Exception {
        ActionablePrepared actionablePrepared = (connection) -> connection.prepareStatement(sql);
        return actionablePrepared.getPreparedStatement(getConnection());
    }

    @Override
    public T find(final String query, final Map<String, Object> map) {
        try (PreparedStatement pstmt = getPreparedStatement(query)) {
            int index = 1;
            for (final String s : map.keySet()) {
                pstmt.setString(index, map.get(s).toString());
                index++;
            }
            ResultSet resultSet = pstmt.executeQuery();
            if (resultSet.next()) {
                return (T) getConstructor(t.getClass(), resultSet);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void save(final T t) {
    }

    private void insert(T t) {
        InsertJdbcTemplate<T> insertJdbcTemplate = new InsertJdbcTemplate<T>() {
            @Override
            public String createQueryForInsert() {
                String tableName = t.getClass().getSimpleName().toUpperCase();
                return String.format("INSERT INTO %sS VALUES (%s)", tableName, getQuestionMark(t.getClass().getFields().length));
            }

            @Override
            public PreparedStatement setValuesForInsert(final PreparedStatement preparedStatement, final Object object) throws IllegalAccessException, SQLException {
                final Field[] fields = object.getClass().getDeclaredFields();
                for (int i = 1; i <= fields.length; i++) {
                    fields[i - 1].setAccessible(true);
                    preparedStatement.setString(i, fields[i - 1].get(object).toString());
                }
                return preparedStatement;
            }

            private String getQuestionMark(int size) {
                StringBuilder questionMarks = new StringBuilder();
                for (int i = 0; i < size - 1; i++) {
                    questionMarks.append("?, ");
                }
                questionMarks.append("?");
                return questionMarks.toString();
            }
        };

        insertJdbcTemplate.insert(t);
    }

    private void update(T t) {
        UpdateJdbcTemplate<T> updateJdbcTemplate = new UpdateJdbcTemplate<T>() {
            @Override
            public String createQueryForUpdate() {
                String tableName = t.getClass().getSimpleName().toUpperCase();
                String id = Arrays.stream(t.getClass().getFields())
                        .findFirst()
                        .get()
                        .toString();
                return String.format("UPDATE %sS SET %s WHERE %s = ?", tableName, getUpdateSetQuestionMark(t.getClass()), id);
            }

            @Override
            public PreparedStatement setValuesForUpdate(final PreparedStatement preparedStatement, final Object object) throws IllegalAccessException, SQLException {
                final Field[] fields = object.getClass().getDeclaredFields();
                fields[0].setAccessible(true);

                for (int i = 1; i < fields.length; i++) {
                    fields[i].setAccessible(true);
                    preparedStatement.setString(i, fields[i].get(object).toString());
                }
                fields[0].setAccessible(true);
                preparedStatement.setString(fields.length, fields[0].get(object).toString());
                return preparedStatement;
            }

            private String getUpdateSetQuestionMark(Class clazz) {
                List<String> mark = new ArrayList<>();
                final Field[] fields = clazz.getDeclaredFields();
                for (int i = 1; i < fields.length; i++) {
                    mark.add(String.format("%s = ?", fields[i].getName()));
                }
                return String.join(",", mark);
            }


        };
        updateJdbcTemplate.update(t);
    }

    @Override
    public T findById(final V v) {
        final Field[] fields = t.getClass().getDeclaredFields();
        fields[0].setAccessible(true);
        String tableName = t.getClass().getSimpleName().toUpperCase();
        String sql = String.format("SELECT * FROM %sS WHERE %s = ?", tableName, fields[0].getName());

        try (PreparedStatement pstmt = getPreparedStatement(sql)) {
            pstmt.setString(1, (String) v);

            ResultSet resultSet = pstmt.executeQuery();
            if (resultSet.next()) {
                return (T) getConstructor(t.getClass(), resultSet);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public List<T> findAll() {
        String tableName = t.getClass().getSimpleName().toUpperCase();
        String sql = String.format("SELECT * FROM %sS", tableName);
        List<T> objects = new ArrayList<>();

        try (PreparedStatement pstmt = getPreparedStatement(sql)) {
            ResultSet resultSet = pstmt.executeQuery();
            while (resultSet.next()) {
                objects.add((T) getConstructor(t.getClass(), resultSet));
            }
            return objects;

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private Object getConstructor(Class clazz, ResultSet resultSet) throws Exception {
        final Field[] fields = clazz.getDeclaredFields();
        Object[] values = new Object[fields.length];
        Class[] types = new Class[fields.length];
        for (int i = 1; i <= fields.length; i++) {
            values[i - 1] = resultSet.getString(i);
            types[i - 1] = fields[i - 1].getType();
        }
        Constructor<?> declaredConstructor = clazz.getDeclaredConstructor(types);
        return declaredConstructor.newInstance(values);
    }
}
