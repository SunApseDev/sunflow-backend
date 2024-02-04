package kr.sunapse.sunflow

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "tbl_geometry")
data class Geometry(
        @Column(name = "id")
        @Id @GeneratedValue(strategy = GenerationType.AUTO) val id: Long,
        @Column(name = "pk_num")
        val pkNum: String,
        @Column(name = "juso_old")
        val jusoOld: String,
        @Column(name = "juso_new")
        val jusoNew: String,
        @Column(name = "si")
        val si: String,
        @Column(name = "bld_nm")
        val bldNm: String,
        @Column(name = "bld_use")
        val bldUse: String,
        @Column(name = "bld_use2")
        val bldUse2: String,
        @Column(name = "tot_area")
        val totArea: Double,
        @Column(name = "sedae")
        val sedae: Double,
        @Column(name = "bld_points")
        val bldPoints: String,
        @Column(name = "bld_h")
        val bldH: Double,
        @Column(name = "bld_row_u")
        val bldRowU: Long,
        @Column(name = "bld_row_d")
        val bldRowD: Long,
        @Column(name = "haebal_h")
        val haebalH: Double,
//        val act : String?,
//        val eUseE : String?,
//        val eUseE : String?,
        @Column(name = "gltf_url")
        val gltfUrl: String,
        @Column(name = "pv_r_area")
        val pvRArea: Double?,
        @Column(name = "pv_s_area")
        val pvSArea: Double?,
        @Column(name = "pv_e_area")
        val pvEArea: Double?,
        @Column(name = "pv_w_area")
        val pvWArea: Double?,
        @Column(name = "pv_r_num")
        val pvRNum: Double?,
        @Column(name = "pv_s_num")
        val pvSNum: Long?,
        @Column(name = "pv_e_num")
        val pvENum: Long?,
        @Column(name = "pv_w_num")
        val pvWNum: Long?,
        @Column(name = "pv_r_cap")
        val pvRCap: Double?,
        @Column(name = "pv_s_cap")
        val pvSCap: Double?,
        @Column(name = "pv_e_cap")
        val pvECap: Double?,
        @Column(name = "pv_w_cap")
        val pvWCap: Double?,
        @Column(name = "pv_r_gen_c")
        val pvRGenC: Double?,
        @Column(name = "pv_s_gen_c")
        val pvSGenC: Double?,
        @Column(name = "pv_e_gen_c")
        val pvEGenC: Double?,
        @Column(name = "pv_w_gen_c")
        val pvWGenC: Double?,
        @Column(name = "pv_r_gen_e")
        val pvRGenE: Double?,
        @Column(name = "pv_s_gen_e")
        val pvSGenE: Double?,
        @Column(name = "pv_e_gen_e")
        val pvEGenE: Double?,
        @Column(name = "pv_w_gen_e")
        val pvWGenE: Double?
) {

}