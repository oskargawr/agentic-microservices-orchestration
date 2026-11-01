package pl.edu.pw.thesis.inventory.persistence;

public record StockRow(String sku, int onHand, int held) {
    public int available() {
        return onHand - held;
    }
}
