package baritone.pathing.seed.pregen;

public final class SectionConnectivity {

    private final int width;
    private final int height;
    private final int depth;
    private final UnionFind unionFind;
    private int faceMask;

    public SectionConnectivity(int width, int height, int depth) {
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.unionFind = new UnionFind(width * height * depth);
    }

    public void unionIfPassable(int x, int y, int z, boolean passable) {
        if (!passable) {
            return;
        }
        int index = index(x, y, z);
        if (x > 0) {
            unionFind.union(index, index(x - 1, y, z));
        }
        if (y > 0) {
            unionFind.union(index, index(x, y - 1, z));
        }
        if (z > 0) {
            unionFind.union(index, index(x, y, z - 1));
        }
        if (x == 0) {
            faceMask |= 1 << 0;
        }
        if (x == width - 1) {
            faceMask |= 1 << 1;
        }
        if (z == 0) {
            faceMask |= 1 << 2;
        }
        if (z == depth - 1) {
            faceMask |= 1 << 3;
        }
        if (y == 0) {
            faceMask |= 1 << 4;
        }
        if (y == height - 1) {
            faceMask |= 1 << 5;
        }
    }

    public int faceMask() {
        return faceMask;
    }

    public int[] parentsArray() {
        return unionFind.parents();
    }

    private int index(int x, int y, int z) {
        return (y * depth + z) * width + x;
    }

    private static final class UnionFind {

        private final int[] parent;
        private final int[] rank;

        UnionFind(int size) {
            this.parent = new int[size];
            this.rank = new int[size];
            for (int i = 0; i < size; i++) {
                parent[i] = i;
            }
        }

        int[] parents() {
            return parent;
        }

        int find(int value) {
            if (parent[value] == value) {
                return value;
            }
            parent[value] = find(parent[value]);
            return parent[value];
        }

        void union(int a, int b) {
            int rootA = find(a);
            int rootB = find(b);
            if (rootA == rootB) {
                return;
            }
            if (rank[rootA] < rank[rootB]) {
                parent[rootA] = rootB;
            } else if (rank[rootA] > rank[rootB]) {
                parent[rootB] = rootA;
            } else {
                parent[rootB] = rootA;
                rank[rootA]++;
            }
        }
    }
}
