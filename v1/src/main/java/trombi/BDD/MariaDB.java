package trombi.BDD;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class MariaDB {

    private static Connection CONNECTION = null;

    /**
     * @return Instance de la connection à la BDD
     * @throws SQLException
     */
    public static Connection getConnection() throws SQLException {
        if (CONNECTION == null) {

            // JScH data
            String ssh_username = "zprojet";
            String ssh_password = "V#M135ES1R!?";
            String ssh_host = "TROMBINOSCOPE.esir.univ-rennes1.fr";
            int ssh_port = 22;

            String ssh_RemoteHost = "localhost";
            int remotePort = 3306;
            int localPort = 0;
            
            try {
                localPort = sshTunnel(ssh_username,ssh_password,ssh_host,ssh_port,ssh_RemoteHost,remotePort);
            } catch (JSchException e) {
                e.printStackTrace();
                System.out.println("UwU");
            }
            finally {}

            String BDD_username = "root";
            String BDD_password = "trombipw";
            String jdbcUrl = "jdbc:mariadb://localhost:" + localPort +"/datatest";

            CONNECTION = DriverManager.getConnection(jdbcUrl, BDD_username, BDD_password);
        }
        return CONNECTION;
    }

    /**
     * 
     * @param ssh_username
     * @param ssh_password
     * @param ssh_host
     * @param ssh_port
     * @param ssh_RemoteHost
     * @param remotePort
     * @throws JSchException
     */
    public static int sshTunnel(String ssh_username, String ssh_password, String ssh_host, int ssh_port,
        String ssh_RemoteHost, int remotePort) throws JSchException 
    {
            Session session = null;
            session = new JSch().getSession(ssh_username, ssh_host, ssh_port);
            session.setPassword(ssh_password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();
            return session.setPortForwardingL(0, ssh_RemoteHost, remotePort);
    }

    /**
     * Conversion de XLSX en base de données.
     *
     * @param xlsx le nom du fichier à convertir (ex: "ESIR.xlsx")
     * @throws SQLException
     */
    public static void transformXLSXToBDD(String xlsx) throws SQLException {
        Connection connection = getConnection();
        try (FileInputStream fileInputStream = new FileInputStream(xlsx)) {
            Workbook workbook = new XSSFWorkbook(fileInputStream);

            // Assuming the data is in the first sheet
            Sheet sheet = workbook.getSheetAt(0);

            // Assuming the first row contains column names
            Row headerRow = sheet.getRow(0);
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                int lastCellNum = row.getLastCellNum();
                try (PreparedStatement statement = connection.prepareStatement(
                        """
                                  REPLACE ELEVE(prenom, nom, email, specialite, option, td, tp, tdMut, tpMut, ang, innov, mana, expr, annee)
                                  VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                                """)) {
                    for (int columnIndex = 1; columnIndex <= lastCellNum; columnIndex++) {
                        Cell cell = row.getCell(columnIndex - 1);
                        if (cell != null) {
                            switch (cell.getCellType()) {
                                case STRING -> statement.setString(columnIndex,
                                        cell.getStringCellValue());
                                case NUMERIC -> {
                                    if (DateUtil.isCellDateFormatted(cell)) {
                                        statement.setDate(columnIndex,
                                                (Date) cell.getDateCellValue());
                                    } else {
                                        statement.setInt(columnIndex, (int) cell.getNumericCellValue());
                                    }
                                }
                                case BOOLEAN -> statement.setBoolean(columnIndex,
                                        cell.getBooleanCellValue());
                                default ->
                                    // Handle other cell types as needed
                                    statement.setString(columnIndex, "");
                            }
                        } else {
                            // Cellule nulle, ajouter une chaîne vide
                            statement.setString(columnIndex, "");
                        }
                    }
                    // date non présente
                    statement.setString(14, "2XXX");
                    int rowsInserted = statement.executeUpdate();

                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Insertion terminée");
    }

    /**
     * 
     * @param email     le mail de l'etudiant à qui ajouté une image
     * @param pathImage
     * @throws IOException
     * @throws SQLException
     */
    public static void insertImage(String email, String pathImage) throws IOException, SQLException {
        Connection connection = getConnection();
        File image = new File(pathImage);
        FileInputStream inputStream = null;
        try {
            // create an input stream pointing to the file
            inputStream = new FileInputStream(image);
            try (PreparedStatement statement = connection.prepareStatement("""
                      UPDATE ELEVE SET image = ? WHERE email = ?
                    """)) {
                statement.setString(2, email);
                statement.setBlob(1, inputStream);
                statement.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            throw new IOException("Unable to convert file to byte array. " +
                    e.getMessage());
        } finally {
            // close input stream
            if (inputStream != null) {
                inputStream.close();
            }
        }
        System.out.println("Insertion image pour : " + email);
    }

    public static byte[] getImage(String email) throws IOException, SQLException {
        Connection connection = getConnection();
        try (PreparedStatement statement = connection.prepareStatement("""
                  SELECT image FROM ELEVE WHERE email = ?
                """)) {
            statement.setString(1, email);
            ResultSet resultSet = statement.executeQuery();
            resultSet.next();
            return resultSet.getBytes(1);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Liste des types de conditions pour autoRequest
     */
    public static enum typeCondition {
        OR {
            @Override
            public String toString() {
                return " OR ";
            }
        },
        AND {
            @Override
            public String toString() {
                return " AND ";
            }
        };

        public abstract String toString();
    };

    /**
     * 
     * @param connection
     * @param nomColumnWanted    : La liste des colonnes voulant être récupéré
     *                           dans la base de données
     * @param nomColumnCondition : La liste des colonnes sur lesquelles on pose
     *                           une condition
     * @param condition          : La liste des conditions /!\ doit faire la même
     *                           taille que nomColumnCondition, la condition à
     *                           l'indice 0 vaut pour la column à l'indice 0
     * @return Le ResultSet de la requete
     * @throws SQLException
     */
    public static ResultSet autoRequest(String[] nomColumnWanted, String[] nomColumnCondition,
            String[] condition, typeCondition typeCondition) throws SQLException {
        assert (nomColumnCondition.length == condition.length);
        boolean conflictWantedCondition = false;
        for (String e1 : nomColumnCondition) {
            for (String e2 : nomColumnWanted) {
                conflictWantedCondition = conflictWantedCondition || e1.equals(e2);
            }
        }
        assert (!conflictWantedCondition);
        Connection connection = getConnection();
        String listColumn = "";
        for (int i = 0; i < nomColumnWanted.length; i++) {
            listColumn += " " + nomColumnWanted[i];
            if (i < nomColumnWanted.length - 1)
                listColumn += ",";
        }
        String listCondition = "";
        listCondition += " WHERE ";
        for (int i = 0; i < nomColumnCondition.length; i++) {
            listCondition += nomColumnCondition[i] + " = ?";
            if (i < nomColumnCondition.length - 1)
                listCondition += typeCondition.toString();
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + listColumn + " FROM ELEVE" + listCondition)) {

            for (int i = 0; i < condition.length; i++)
                statement.setString(i + 1, condition[i]);
            return statement.executeQuery();

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
}
