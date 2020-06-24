package com.Exalt.MultiThreading.Domain.Dom;

import com.Exalt.MultiThreading.Domain.Constants;
import com.Exalt.MultiThreading.Domain.Dto.CustomerDto;
import com.Exalt.MultiThreading.Domain.Mapper.ServerMapper;
import com.Exalt.MultiThreading.Domain.Runnable.UpdateServer;
import com.Exalt.MultiThreading.Domain.Service.CustomerService;
import com.Exalt.MultiThreading.Domain.Service.ServerService;
import com.Exalt.MultiThreading.Domain.Runnable.SpanServer;
import com.Exalt.MultiThreading.Infrastructure.Repository.ServerRepository;
import com.devskiller.friendly_id.FriendlyId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ServerProvider {

    @Autowired
    ServerService serverService;

    @Autowired
    CustomerService customerService;

    @Autowired
    FriendlyId friendlyId;

    @Autowired
    ServerMapper serverMapper;

    @Autowired
    ServerRepository serverRepository;

    public static HashMap<String, ServerDom> serversCache = new HashMap<String, ServerDom>();

    enum AllocationMethod {
        Update, Span
    }

    public void RentServer(String customerId, int space) {
        System.out.println(this + "  " + Thread.currentThread());
        AllocationMethod allocationMethod;
        String serverId = checkAvailableServer(space);
        allocationMethod = (serverId.equals("-1")) ?
                AllocationMethod.Span : AllocationMethod.Update;

        // allocate the space in existing server that have enough space
        if (allocationMethod == AllocationMethod.Update) {
            //update server locally
            ServerDom serverDom = updateLocally(serverId, space);

            //thread to update server in the database
            UpdateServer updateServer = new UpdateServer(serverRepository, serverMapper, serverDom);
            Thread updateServerThread = new Thread(updateServer);
            updateServerThread.start();

        }
        // span new server when there no server with enough space
        else if (allocationMethod == AllocationMethod.Span) {
            //create locally
            ServerDom serverDom = spanLocally(space);
            //spanning the server
            SpanServer spanServer = new SpanServer(this, serverRepository, serverMapper, serverDom);
            Thread spanServerThread = new Thread(spanServer);
            spanServerThread.start();
        }

        //update customer
        CustomerDto customerDto = customerService.getCustomer(customerId);
        customerDto.setReservedSpace(customerDto.getReservedSpace() + space);
        customerService.updateCustomer(customerDto);
    }

    public ServerDom spanLocally(int space) {
        String serverId = friendlyId.createFriendlyId();
        ServerDom serverDom = new ServerDom();
        serverDom.setActive(false);
        serverDom.setId(serverId);
        serverDom.setRemainingCapacity(Constants.ServerMaximumCapacity - space);
        serversCache.put(serverId, serverDom);
        return serverDom;
    }

    public ServerDom updateLocally(String serverId, int space) {
        ServerDom serverDom = serversCache.get(serverId);
        serverDom.setRemainingCapacity(serverDom.getRemainingCapacity() - space);
        serversCache.put(serverId, serverDom);
        return serverDom;
    }

    public String checkAvailableServer(int targetSpace) {
        serversCache = sortBySpace(serversCache);
        for (Map.Entry<String, ServerDom> server : serversCache.entrySet()) {
            if (server.getValue().getRemainingCapacity() >= targetSpace) {
                return server.getKey();
            }
        }
        //return -1 if there is no available server with enough space
        return "-1";
    }

    public static HashMap<String, ServerDom> sortBySpace(HashMap<String, ServerDom> hm) {
        List<Map.Entry<String, ServerDom>> list =
                new LinkedList<>(hm.entrySet());

        Collections.sort(list, new Comparator<Map.Entry<String, ServerDom>>() {
            public int compare(Map.Entry<String, ServerDom> o1,
                               Map.Entry<String, ServerDom> o2) {
                return ((Integer) o1.getValue().getRemainingCapacity())
                        .compareTo((Integer) o2.getValue().getRemainingCapacity());
            }
        });

        HashMap<String, ServerDom> temp = new LinkedHashMap<>();
        for (Map.Entry<String, ServerDom> aa : list) {
            temp.put(aa.getKey(), aa.getValue());
        }
        return temp;
    }

    public void deleteServerLocal(String id) {
        serversCache.remove(id);
    }

    public void clearServersLocal() {
        serversCache.clear();
    }

    public void activateServerLocal(String id) {
        serversCache.get(id).setActive(true);
    }
}