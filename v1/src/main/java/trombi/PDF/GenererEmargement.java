package trombi.PDF;

import java.util.ArrayList;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.UnitValue;

public class GenererEmargement {

    //path de destination pour le fichier créé
    private final String dest;

    public GenererEmargement(String dest) {
        this.dest = dest;
    }

    /**
     * Crée le PDF d'émargement (création, remplissage et arrangement des cellules)
     *
     * @param nomEleve la liste des élèves qu'on veut mettre dans la feuille d'émargement
     * @param nomPdf titre de la feuille d'émargement
     * @throws Exception
     */
    protected void manipulatePdf(ArrayList<String> nomEleve, String nomPdf) throws Exception {
        //File file = new File(this.dest);
        //file.getParentFile().mkdirs();

        //Création du document
        PdfDocument pdfDoc = new PdfDocument(new PdfWriter(this.dest));
        Document doc = new Document(pdfDoc);

        //Création d'un paragraphe (champ de texte) avec le nom du document 
        Paragraph titre = new Paragraph(nomPdf);
        titre.setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER);
        titre.setBold();
        doc.add(titre);

        int nbCellules = 6;//Nombre de séances

        //Création de la table avec nbCellules + 1(nom de l'élève) colonnes
        Table table = new Table(UnitValue.createPercentArray(nbCellules + 1)).useAllAvailableWidth();
        table.setMargin(15);

        //Labelisation des colonnes
        table.addCell(new Cell().add(new Paragraph("Nom")));
        for (int i = 0; i < nbCellules; i++) {
            Cell cell = new Cell();
            Paragraph pNom = new Paragraph("Séance " + (i + 1));
            cell.add(pNom);
            table.addCell(cell);
        }

        //Ajout des noms des élèves sur chaque ligne
        for (String eleve : nomEleve) {
            Cell cell = new Cell(); //Création de la cellule
            Paragraph pNom = new Paragraph(eleve); //Création d'un "paragraphe" pour le nom de l'élève
            cell.add(pNom);
            table.addCell(cell);

            //Ajout de cellules vide pour pouvoir indiquer la présence à la séance concernée
            
            for (int i = 0; i < nbCellules; i++) {
                Cell emptyCell = new Cell();
                table.addCell(emptyCell);
            }
        }

        doc.add(table);
        doc.close();
    }

}

