public class Coordinator extends Process {
    public Coordinator(int id) {
        super(id);
        this.isCoordinator = true;
        this.coordinatorId = id;
    }
}
