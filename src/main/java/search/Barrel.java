package search;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface Barrel extends Remote {
    // Adiciona dados ao índice e confirma sucesso (true) ou falha (false)
    boolean addToIndex(PageIndexData data) throws RemoteException;

    // Search for pages matching all terms in the query string.
    List<PageResult> search(String query, int page, int pageSize) throws RemoteException;

    // Return the list of known inlinks.
    List<String> getInlinks(String url) throws RemoteException;

    // Number of indexed URLs known by this Barrel.
    int getIndexedUrlCount() throws RemoteException;

    // Export a chunk of the current index for recovery.
    List<PageIndexData> exportAll(int offset, int limit) throws RemoteException;
}
