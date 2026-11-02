package pl.edu.pw.thesis.experiment.oracle.snapshot;

public record StockView(String sku, int onHand, int held, int heldSumFromHeldReservations) {}
