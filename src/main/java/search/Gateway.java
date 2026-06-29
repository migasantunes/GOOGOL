package search; // Package holding search-related RMI interfaces

import java.rmi.Remote; // RMI types for remote method invocation
import java.rmi.RemoteException; // Collections used in method signatures
import java.util.List;

/**
 * Remote gateway for clients and downloaders. Holds the URL queue and proxies searches to Barrels.
 */
public interface Gateway extends Remote { // RMI remote interface; exposed across JVMs
    // Note: methods throw RemoteException because network calls can fail

    // Client-facing: search indexed pages with pagination (page is 0/1-based as defined elsewhere)
    List<PageResult> search(String query, int page) throws RemoteException;

    // Client-facing: list of URLs that link to the given URL (inbound links)
    List<String> getInlinks(String url) throws RemoteException;

    // Client-facing: enqueue a new URL to be crawled
    void submitUrl(String url) throws RemoteException;

    // Downloader-facing: fetch the next URL to crawl from the queue
    String takeNextUrl() throws RemoteException;

    // Stats-facing: retrieve aggregated system metrics
    SystemStats getSystemStats() throws RemoteException;

    // Semaphore: pause/resume downloaders (crawl distribution)
    void setCrawlPaused(boolean paused) throws RemoteException; // Control crawler activity
    boolean isCrawlPaused() throws RemoteException; // Check if crawling is paused

    // Registo dinâmico de um Barrel (host, porto, nome no registry)
    void registerBarrel(String host, int port, String name) throws RemoteException;

    // Indexação via Gateway: envia PageIndexData para os Barrels registados e devolve se pelo menos um confirmou
    boolean indexPage(PageIndexData data) throws RemoteException;
}
