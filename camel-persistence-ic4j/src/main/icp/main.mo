import Array "mo:base/Array";
import HashMap "mo:base/HashMap";
import Int "mo:base/Int";
import Nat "mo:base/Nat";
import Nat32 "mo:base/Nat32";
import Text "mo:base/Text";
import Time "mo:base/Time";

persistent actor {
    type AppendResult = {
        previousVersion : Nat;
        nextVersion : Nat;
        duplicate : Bool;
    };

    type PersistedEvent = {
        eventId : Text;
        flowType : Text;
        flowId : Text;
        sequence : Nat;
        eventType : Text;
        payloadJson : Text;
        occurredAt : Text;
        idempotencyKey : Text;
    };

    type StateEnvelope = {
        flowType : Text;
        flowId : Text;
        version : Nat;
        snapshotVersion : Nat;
        snapshotJson : Text;
        lastUpdatedAt : Text;
        metadataJson : Text;
    };

    type RehydratedState = {
        envelope : StateEnvelope;
        tailEvents : [PersistedEvent];
    };

    type PersistenceError = {
        code : Text;
        message : Text;
    };

    type AppendEventsRequest = {
        flowType : Text;
        flowId : Text;
        expectedVersion : Nat;
        events : [PersistedEvent];
        idempotencyKey : Text;
    };

    type AppendEventsResponse = {
        result : ?AppendResult;
        error : ?PersistenceError;
    };

    type WriteSnapshotRequest = {
        flowType : Text;
        flowId : Text;
        version : Nat;
        snapshotJson : Text;
        metadataJson : Text;
    };

    type MutationResponse = {
        result : ?Bool;
        error : ?PersistenceError;
    };

    type FlowKey = {
        flowType : Text;
        flowId : Text;
    };

    type ReadEventsRequest = {
        flowType : Text;
        flowId : Text;
        afterVersion : Nat;
        limit : Nat32;
    };

    type FlowState = {
        var version : Nat;
        var events : [PersistedEvent];
        var snapshotVersion : Nat;
        var snapshotJson : Text;
        var metadataJson : Text;
        var lastUpdatedAt : Text;
        var idempotencyKeys : [Text];
    };

    transient let flows = HashMap.HashMap<Text, FlowState>(16, Text.equal, Text.hash);

    public shared func appendEvents(request : AppendEventsRequest) : async AppendEventsResponse {
        let state = getOrCreate(request.flowType, request.flowId);
        if (request.idempotencyKey != "" and containsText(state.idempotencyKeys, request.idempotencyKey)) {
            return {
                result = ?{
                    previousVersion = state.version;
                    nextVersion = state.version;
                    duplicate = true;
                };
                error = null;
            };
        };
        if (state.version != request.expectedVersion) {
            return {
                result = null;
                error = ?{
                    code = "OPTIMISTIC_CONFLICT";
                    message = "Expected version " # Nat.toText(request.expectedVersion) # " but current version is " # Nat.toText(state.version);
                };
            };
        };

        let previousVersion = state.version;
        let assigned = Array.tabulate<PersistedEvent>(
            request.events.size(),
            func(index : Nat) : PersistedEvent {
                let event = request.events[index];
                {
                    eventId = event.eventId;
                    flowType = request.flowType;
                    flowId = request.flowId;
                    sequence = previousVersion + index + 1;
                    eventType = event.eventType;
                    payloadJson = event.payloadJson;
                    occurredAt = event.occurredAt;
                    idempotencyKey = if (event.idempotencyKey == "") request.idempotencyKey else event.idempotencyKey;
                }
            }
        );

        state.events := Array.append<PersistedEvent>(state.events, assigned);
        state.version := previousVersion + assigned.size();
        state.lastUpdatedAt := nowText();
        if (request.idempotencyKey != "") {
            state.idempotencyKeys := Array.append<Text>(state.idempotencyKeys, [request.idempotencyKey]);
        };

        return {
            result = ?{
                previousVersion = previousVersion;
                nextVersion = state.version;
                duplicate = false;
            };
            error = null;
        };
    };

    public shared func writeSnapshot(request : WriteSnapshotRequest) : async MutationResponse {
        let state = getOrCreate(request.flowType, request.flowId);
        if (request.version > state.version) {
            return {
                result = null;
                error = ?{
                    code = "OPTIMISTIC_CONFLICT";
                    message = "Snapshot version " # Nat.toText(request.version) # " is ahead of current version " # Nat.toText(state.version);
                };
            };
        };
        state.snapshotVersion := request.version;
        state.snapshotJson := request.snapshotJson;
        state.metadataJson := request.metadataJson;
        state.lastUpdatedAt := nowText();
        return { result = ?true; error = null };
    };

    public shared query func rehydrate(key : FlowKey) : async RehydratedState {
        switch (flows.get(flowKey(key.flowType, key.flowId))) {
            case null emptyState(key.flowType, key.flowId);
            case (?state) {
                {
                    envelope = envelope(key.flowType, key.flowId, state);
                    tailEvents = Array.filter<PersistedEvent>(state.events, func(event) { event.sequence > state.snapshotVersion });
                }
            };
        }
    };

    public shared query func readEvents(request : ReadEventsRequest) : async [PersistedEvent] {
        switch (flows.get(flowKey(request.flowType, request.flowId))) {
            case null [];
            case (?state) {
                let filtered = Array.filter<PersistedEvent>(state.events, func(event) { event.sequence > request.afterVersion });
                let count = Nat.min(filtered.size(), Nat32.toNat(request.limit));
                Array.tabulate<PersistedEvent>(count, func(index) { filtered[index] });
            };
        }
    };

    func getOrCreate(flowType : Text, flowId : Text) : FlowState {
        let key = flowKey(flowType, flowId);
        switch (flows.get(key)) {
            case (?state) state;
            case null {
                let state : FlowState = {
                    var version = 0;
                    var events = [];
                    var snapshotVersion = 0;
                    var snapshotJson = "{}";
                    var metadataJson = "{}";
                    var lastUpdatedAt = nowText();
                    var idempotencyKeys = [];
                };
                flows.put(key, state);
                state;
            };
        }
    };

    func emptyState(flowType : Text, flowId : Text) : RehydratedState {
        {
            envelope = {
                flowType = flowType;
                flowId = flowId;
                version = 0;
                snapshotVersion = 0;
                snapshotJson = "{}";
                lastUpdatedAt = "0";
                metadataJson = "{}";
            };
            tailEvents = [];
        }
    };

    func envelope(flowType : Text, flowId : Text, state : FlowState) : StateEnvelope {
        {
            flowType = flowType;
            flowId = flowId;
            version = state.version;
            snapshotVersion = state.snapshotVersion;
            snapshotJson = state.snapshotJson;
            lastUpdatedAt = state.lastUpdatedAt;
            metadataJson = state.metadataJson;
        }
    };

    func containsText(values : [Text], value : Text) : Bool {
        for (current in values.vals()) {
            if (current == value) {
                return true;
            };
        };
        false;
    };

    func flowKey(flowType : Text, flowId : Text) : Text {
        flowType # ":" # flowId;
    };

    func nowText() : Text {
        Int.toText(Time.now());
    };
};