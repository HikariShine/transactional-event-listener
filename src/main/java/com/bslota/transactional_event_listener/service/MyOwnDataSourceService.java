package com.bslota.transactional_event_listener.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;

@Service
public class MyOwnDataSourceService {

    @Autowired
    private DataSource dataSource;

    /**
     * 测试savepoint场景
     * savepoint包含功能：
     * 1. 设置：在当前执行节点创建一个savepoint
     * 2. 释放：释放某个savepoint是指无效此savepoint
     * 3. 回滚：回滚至某个savepoint，此时除了此savepoint外，其他savepoint自动释放。回滚时不包含savepoint，则无效全部savepoint
     * 测试场景：
     * 1. 三个savepoint，回滚到中间的一个，整个的提交情况
     * 2. 三个savepoint，回滚到中间的一个，还可以回滚这个savepoint之前的吗
     * 3. 三个savepoint，回滚到中间的一个，还可以回滚这个savepoint之后的吗，此时回滚效果是以中间的为准还是后面的为准
     *    即验证多次rollback是否以最后的rollback为准。
     * 4. 三个savepoint，回滚到中间的一个，还可以再回滚到这一个吗，即验证一个rollback后，会不会把这个savepoint给释放
     * 5. 三个savepoint，不带参数回滚，还可以再回滚到其中某个savepoint吗
     * 6. 三个savepoint，release中间的一个，还可以rollback到这个savepoint之前吗
     * 7. 三个savepoint，release中间的一个，还可以rollback到这个savepoint之后吗
     * 8. 三个savepoint，release中间的一个，还可以rollback到这个savepoint吗
     */
    public void doTestSavepoint() {
        try {
            Connection connection = dataSource.getConnection();
            connection.setAutoCommit(false);
            Statement statement = connection.createStatement();
            statement.execute("INSERT INTO customer(name, email) VALUES ('Matt1', 'matt@gmail.com')");
            Savepoint savepoint1 = connection.setSavepoint("Matt1");
            statement.execute("INSERT INTO customer(name, email) VALUES ('Matt2', 'matt@gmail.com')");
            Savepoint savepoint2 = connection.setSavepoint("Matt2");
            statement.execute("INSERT INTO customer(name, email) VALUES ('Matt3', 'matt@gmail.com')");
            Savepoint savepoint3 = connection.setSavepoint("Matt3");
            statement.execute("INSERT INTO customer(name, email) VALUES ('Matt4', 'matt@gmail.com')");
            /* 验证场景1，回滚到savepoint2，此时savepoint2之前的insert提交成功，之后的被回滚
             * 小tip: 回滚事务时，insert的主键不会被回滚，假设回滚2条，那么下次新增的数据主键id依然会增加3，因为自增和insert是分开的
            connection.rollback(savepoint2);
            connection.commit();
             */
            /* 验证场景2，回滚到savepoint2，再回滚到savepoint1
            此时savepoint1之前的insert被提交，也就是说可以回滚到之前的
            connection.rollback(savepoint2);
            connection.rollback(savepoint1);
            connection.commit();
            */
            /* 验证场景3，中间的回滚之后，回滚后面的，会抛出异常
            异常信息为 com.mysql.jdbc.exceptions.jdbc4.MySQLSyntaxErrorException: SAVEPOINT Matt3 does not exist
            说明回滚savepoint2之后，会把savepoint2之后的savepoint释放
            connection.rollback(savepoint2);
            connection.rollback(savepoint3);
            connection.commit();
            */
            /* 验证场景4，同一个savepoint可以回滚多次
            connection.rollback(savepoint2);
            connection.rollback(savepoint2);
            connection.commit();
            */
            /* 验证场景5，不带savepoint的rollback相当于rollback到第一个savepoint之前的隐藏savepoint，不可以再rollback到其他savepoint
            connection.rollback();
            connection.rollback(savepoint2);
            connection.commit();
            */
            /* 验证场景6，可以回滚到之前
            connection.releaseSavepoint(savepoint2);
            connection.rollback(savepoint1);
            connection.commit();
            */
            /* 验证场景7，可以回滚到之后
            connection.releaseSavepoint(savepoint2);
            connection.rollback(savepoint3);
            connection.commit();
            */
            /* 验证场景8，可以回滚到这个savepoint
            connection.releaseSavepoint(savepoint2);
            connection.rollback(savepoint2);
            connection.commit();
            */
            // 上面三个场景很奇怪啊，看起来好像是releaseSavepoint没有什么用
            // 答案确实是没有用，正常情况下releaseSavepoint是有效的，效果是释放当前及之前的所有savepoint
            // 而上面的示例为什么没用呢，因为mysql驱动并没有实现这个方法，不知道不实现这个方法是基于什么考虑
            // com.mysql.jdbc.ConnectionImpl和com.mysql.cj.jdbc.ConnectionImpl的releaseSavepoint方法是一个空的逻辑，不执行任何操作
            // 而mysql数据库原始的SAVEPOINT Matt2;RELEASE SAVEPOINT Matt2;ROLLBACK TO SAVEPOINT Matt2;是有效的，会报错
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * 测试事务提交，当设置autoCommit时，会自动把之前未提交的内容提交，相当于执行了事务提交
     */
    public void doTestAutoCommit() {
        try {
            Connection connection = dataSource.getConnection();
            connection.setAutoCommit(false);
            Statement statement = connection.createStatement();
            statement.execute("INSERT INTO customer(name, email) VALUES ('Matt1', 'matt@gmail.com')");
            connection.setAutoCommit(true);
            // connection.commit();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void doClear() {
        try {
            Connection connection = dataSource.getConnection();
            Statement statement = connection.createStatement();
            statement.execute("delete from customer");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
